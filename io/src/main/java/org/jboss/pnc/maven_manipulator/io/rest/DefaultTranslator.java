/*
 * Copyright (C) 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.maven_manipulator.io.rest;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_OK;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.da.lookup.model.MavenLatestRequest;
import org.jboss.da.lookup.model.MavenLookupRequest;
import org.jboss.pnc.maven_manipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.maven_manipulator.common.json.DependencyAnalyserResult;
import org.jboss.pnc.maven_manipulator.common.json.ErrorMessage;
import org.jboss.pnc.maven_manipulator.common.util.GAVUtils;
import org.jboss.pnc.maven_manipulator.common.util.JSONUtils.InternalObjectMapper;
import org.jboss.pnc.maven_manipulator.common.util.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.resilience.otel.OTelCLIHelper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.Getter;

/**
 * @author ncross@redhat.com
 * @author vdedik@redhat.com
 * @author jsenko@redhat.com
 */
public class DefaultTranslator
        implements Translator {
    private static final GenericType<List<DependencyAnalyserResult>> lookupType = new GenericType<List<DependencyAnalyserResult>>() {
    };

    public enum Endpoint {
        LOOKUP_GAVS("lookup/maven"),
        LOOKUP_LATEST("lookup/maven/latest");

        @Getter
        private final String endpoint;

        Endpoint(String s) {
            this.endpoint = s;
        }

        @Override
        public String toString() {
            return endpoint;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String endpointUrl;

    private final int initialRestMaxSize;

    private final int initialRestMinSize;

    private final Boolean brewPullActive;

    private final String mode;

    private final Map<String, String> restHeaders;

    private final Map<String, String> otelHeaders = new HashMap<>();

    private final int retryDuration;

    private final int restConnectionTimeout;

    private final int restSocketTimeout;

    static {
        // According to https://kong.github.io/unirest-java/#configuration the default connection timeout is 10000
        // and the default socketTimeout is 60000.
        // We have increased the first to 30 seconds and the second to 10 minutes.
        Unirest.config()
                .socketTimeout(600000)
                .connectTimeout(30000)
                .setObjectMapper(new InternalObjectMapper(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    /**
     * @param endpointUrl is the URL to talk to.
     * @param restMaxSize initial (maximum) size of the rest call; if zero will send everything.
     * @param restMinSize minimum size for the call
     * @param brewPullActive flag saying if brew pull should be used for version retrieval
     * @param mode lookup mode, either PERSISTENT, TEMPORARY, SERVICE or SERVICE-TEMPORARY
     * @param restHeaders the headers to pass to the endpoint
     * @param restConnectionTimeout the timeout for the REST request; defaults to
     *        {@link Translator#DEFAULT_CONNECTION_TIMEOUT_SEC}
     * @param restSocketTimeout the timeout for the REST socket calls; defaults to
     *        {@link Translator#DEFAULT_SOCKET_TIMEOUT_SEC}
     * @param restRetryDuration the retry duration configuration; ; defaults to {@link Translator#RETRY_DURATION_SEC}
     */
    public DefaultTranslator(
            String endpointUrl,
            int restMaxSize,
            int restMinSize,
            Boolean brewPullActive,
            String mode,
            Map<String, String> restHeaders,
            int restConnectionTimeout,
            int restSocketTimeout,
            int restRetryDuration) {
        this.brewPullActive = brewPullActive;
        this.mode = mode;
        this.endpointUrl = endpointUrl + (isNotBlank(endpointUrl) ? endpointUrl.endsWith("/") ? "" : "/" : "");
        this.initialRestMaxSize = restMaxSize;
        this.initialRestMinSize = restMinSize;
        this.restHeaders = restHeaders;
        this.restConnectionTimeout = restConnectionTimeout;
        this.restSocketTimeout = restSocketTimeout;
        this.retryDuration = restRetryDuration;

        if (OTelCLIHelper.otelEnabled()) {
            SpanContext current = Span.current().getSpanContext();
            if (current.isValid()) {
                otelHeaders.put("trace-id", current.getTraceId());
                otelHeaders.put("span-id", current.getSpanId());
                otelHeaders.put(
                        "tracestate",
                        current.getTraceState()
                                .asMap()
                                .entrySet()
                                .stream()
                                .map(Objects::toString)
                                .collect(Collectors.joining(",")));
                // Code from pnc-common to avoid transitively including that and pnc-api in the classpath
                otelHeaders.put(
                        "traceparent",
                        String.format(
                                "%s-%s-%s-%s",
                                "00",
                                current.getTraceId(),
                                current.getSpanId(),
                                current.getTraceFlags().asHex()));
            } else {
                logger.warn("Invalid span context {}", current);
            }
        }
    }

    /**
     * Translate the versions.
     *
     * <pre>
     * [
     *   {
     *     "groupId": "com.google.guava",
     *     "artifactId": "guava",
     *     "version": "13.0.1"
     *   }
     * ]
     * </pre>
     *
     * This equates to a List of ProjectVersionRef.
     *
     * <pre>
     * {
     *     "productNames": [],
     *     "productVersionIds": [],
     *     "repositoryGroup": "",
     *     "gavs": [
     *       {
     *         "groupId": "com.google.guava",
     *         "artifactId": "guava",
     *         "version": "13.0.1"
     *       }
     *     ]
     * }
     * </pre>
     *
     * There may be a lot of them, possibly causing timeouts or other issues.
     * This is mitigated by splitting them into smaller chunks when an error occurs and retrying.
     *
     * @param p the list of ProjectVersionRef to lookup
     * @return the resulting map of ProjectVersionRef to new Version
     * @throws RestException if an error occurs
     */
    @Override
    public Map<ProjectVersionRef, String> lookupVersions(List<ProjectVersionRef> p) throws RestException {
        return internalLookup(Endpoint.LOOKUP_GAVS, p);
    }

    /**
     * This is similar to {@link DefaultTranslator#lookupVersions}} in that it contacts DA to lookup versions but this
     * call ignores suffix priority and returns the latest version for the configured suffix mode. This is typically
     * used for project version lookups.
     *
     * @param p the list of ProjectVersionRef to lookup
     * @return the resulting map of ProjectVersionRef to new Version
     * @throws RestException if an error occurs
     */
    @Override
    public Map<ProjectVersionRef, String> lookupProjectVersions(List<ProjectVersionRef> p) throws RestException {
        return internalLookup(Endpoint.LOOKUP_LATEST, p);
    }

    private void partition(Endpoint endpointType, List<ProjectVersionRef> projects, Queue<Task> queue) {
        if (initialRestMaxSize != 0) {
            if (initialRestMaxSize == -1) {
                autoPartition(endpointType, projects, queue);
            } else {
                userDefinedPartition(endpointType, projects, queue);
            }
        } else {
            noOpPartition(endpointType, projects, queue);
        }
    }

    private void noOpPartition(Endpoint endpointType, List<ProjectVersionRef> projects, Queue<Task> queue) {
        logger.info("Using NO-OP partition strategy");

        queue.add(new Task(projects, endpointUrl, endpointType));
    }

    private void userDefinedPartition(Endpoint endpointType, List<ProjectVersionRef> projects, Queue<Task> queue) {
        logger.info("Using user defined partition strategy");

        // Presplit
        final List<List<ProjectVersionRef>> partition = ListUtils.partition(projects, initialRestMaxSize);

        for (List<ProjectVersionRef> p : partition) {
            queue.add(new Task(p, endpointUrl, endpointType));
        }

        logger.debug("For initial sizing of {} have split the queue into {}", initialRestMaxSize, queue.size());
    }

    private void autoPartition(Endpoint endpointType, List<ProjectVersionRef> projects, Queue<Task> queue) {
        List<List<ProjectVersionRef>> partition;

        if (projects.size() < 600) {
            logger.info(
                    "Using auto partition strategy: {} projects divided in chunks with {} each",
                    projects.size(),
                    128);
            partition = ListUtils.partition(projects, 128);
        } else {
            if (projects.size() > 600 && projects.size() < 1200) {
                logger.info(
                        "Using auto partition strategy: {} projects divided in chunks with {} each",
                        projects.size(),
                        64);
                partition = ListUtils.partition(projects, 64);
            } else {
                logger.info(
                        "Using auto partition strategy: {} projects divided in chunks with {} each",
                        projects.size(),
                        32);
                partition = ListUtils.partition(projects, 32);
            }
        }

        for (List<ProjectVersionRef> p : partition) {
            queue.add(new Task(p, endpointUrl, endpointType));
        }
    }

    private Map<ProjectVersionRef, String> internalLookup(Endpoint endpointType, List<ProjectVersionRef> p)
            throws RestException {
        final List<ProjectVersionRef> projects = p.stream().distinct().collect(Collectors.toList());
        if (p.size() != projects.size()) {
            logger.debug("Eliminating duplicates reduced {} to {}", p.size(), projects.size());
        }
        logger.info("Calling REST client... (with {} GAVs)", projects.size());

        final Queue<Task> queue = new ArrayDeque<>();
        final Map<ProjectVersionRef, String> result = new HashMap<>();
        final long start = System.nanoTime();

        boolean finishedSuccessfully = false;

        try {

            partition(endpointType, projects, queue);

            while (!queue.isEmpty()) {
                Task task = queue.remove();
                task.executeTranslate();
                if (task.isSuccess()) {
                    result.putAll(task.getResult());
                } else {
                    if (task.canSplit() && isRecoverable(task.getStatus())) {
                        if (task.getStatus() == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                            logger.info(
                                    "The DA server is unavailable. Waiting {} before splitting the tasks and retrying",
                                    retryDuration);

                            waitBeforeRetry(retryDuration);
                        }

                        List<Task> tasks = task.split(endpointType);

                        logger.warn(
                                "Failed to translate versions for task @{} due to {}, splitting and retrying. Chunk size was: {} and new chunk size {} in {} segments.",
                                task.hashCode(),
                                task.getStatus(),
                                task.getChunkSize(),
                                tasks.get(0).getChunkSize(),
                                tasks.size());
                        queue.addAll(tasks);
                    } else {
                        if (task.getStatus() < 0) {
                            logger.debug("Caught exception calling server with message {}", task.getErrorMessage());
                        } else {
                            logger.debug("Did not get status {} but received {}", SC_OK, task.getStatus());
                        }

                        throw new RestException(
                                "Received response status {} with message: {}",
                                task.getStatus(),
                                task.getErrorMessage());
                    }
                }
            }
            finishedSuccessfully = true;
        } finally {
            printFinishTime(logger, start, finishedSuccessfully);
        }

        return result;
    }

    private boolean isRecoverable(int httpErrorCode) {
        return httpErrorCode == HttpStatus.SC_GATEWAY_TIMEOUT || httpErrorCode == HttpStatus.SC_SERVICE_UNAVAILABLE;
    }

    private void waitBeforeRetry(int seconds) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
        } catch (InterruptedException e) {
            logger.error("Caught exception while waiting", e);
        }
    }

    private class Task {
        private final List<ProjectVersionRef> chunk;

        private final String endpointUrl;

        private final Endpoint endpointType;

        private Map<ProjectVersionRef, String> result = Collections.emptyMap();

        private int status = -1;

        private Exception exception;

        private String errorString;

        Task(List<ProjectVersionRef> chunk, String endpointUrl, Endpoint endpointType) {
            this.chunk = chunk;
            this.endpointUrl = endpointUrl;
            this.endpointType = endpointType;
        }

        void executeTranslate() {
            HttpResponse<List<DependencyAnalyserResult>> r;

            try {
                final boolean lookup = (endpointType == Endpoint.LOOKUP_GAVS);
                Object request = lookup ? (MavenLookupRequest
                        .builder()
                        .mode(mode)
                        .brewPullActive(brewPullActive)
                        .artifacts(GAVUtils.generateGAVs(chunk))
                        .build())
                        : (MavenLatestRequest
                                .builder()
                                .mode(mode)
                                .artifacts(GAVUtils.generateGAVs(chunk))
                                .build());

                r = Unirest.post(endpointUrl + endpointType)
                        .header("accept", "application/json")
                        .header("Content-Type", "application/json")
                        .headers(restHeaders)
                        .headers(otelHeaders)
                        .connectTimeout(restConnectionTimeout * 1000)
                        .socketTimeout(restSocketTimeout * 1000)
                        .body(request)
                        .asObject(lookupType)
                        .ifSuccess(
                                successResponse -> result = successResponse.getBody()
                                        .stream()
                                        .filter(
                                                f -> lookup ? isNotBlank(f.getBestMatchVersion())
                                                        : isNotBlank(f.getLatestVersion()))
                                        .collect(
                                                Collectors.toMap(
                                                        DependencyAnalyserResult::getProjectVersionRef,
                                                        lookup ? DependencyAnalyserResult::getBestMatchVersion
                                                                : DependencyAnalyserResult::getLatestVersion,
                                                        // If there is a duplicate key, use the original.
                                                        (o, n) -> {
                                                            logger.warn("Located duplicate key {}", o);
                                                            return o;
                                                        })))
                        .ifFailure(failedResponse -> {
                            if (!failedResponse.getParsingError().isPresent()) {
                                logger.debug(
                                        "Parsing error but no message. Status text {}",
                                        failedResponse.getStatusText());
                                throw new ManipulationUncheckedException(failedResponse.getStatusText());
                            } else {
                                String originalBody = failedResponse.getParsingError().get().getOriginalBody();

                                if (originalBody.isEmpty()) {
                                    this.errorString = "No content to read.";
                                } else if (originalBody.startsWith("<")) {
                                    // Read an HTML string.
                                    String stripped = originalBody.replaceAll("<.*?>", "").replaceAll("\n", " ").trim();
                                    logger.debug(
                                            "Read HTML string '{}' rather than a JSON stream; stripping message to '{}'",
                                            originalBody,
                                            stripped);
                                    this.errorString = stripped;
                                } else if (originalBody.startsWith("{\"")) {
                                    this.errorString = failedResponse.mapError(ErrorMessage.class).toString();

                                    logger.debug("Read message string {}, processed to {}", originalBody, errorString);
                                } else if (originalBody.startsWith("javax.validation.ValidationException: ")) {
                                    this.errorString = originalBody;
                                } else {
                                    logger.error(
                                            "HTTP comm failure: {}",
                                            failedResponse.getParsingError().get().getMessage());
                                    throw new ManipulationUncheckedException(
                                            "Problem in HTTP communication with status code {} and message {}",
                                            failedResponse.getStatus(),
                                            failedResponse.getStatusText());
                                }
                            }
                        });

                status = r.getStatus();
            } catch (ManipulationUncheckedException | UnirestException e) {
                exception = e;
                this.status = -1;
            }
        }

        public List<Task> split(Endpoint endpointType) {
            List<Task> res = new ArrayList<>(CHUNK_SPLIT_COUNT);
            if (chunk.size() >= CHUNK_SPLIT_COUNT) {
                // To KISS, overflow the remainder into the last chunk
                int chunkSize = chunk.size() / CHUNK_SPLIT_COUNT;
                for (int i = 0; i < (CHUNK_SPLIT_COUNT - 1); i++) {
                    res.add(new Task(chunk.subList(i * chunkSize, (i + 1) * chunkSize), endpointUrl, endpointType));
                }
                // Last chunk may have different size
                res.add(
                        new Task(
                                chunk.subList((CHUNK_SPLIT_COUNT - 1) * chunkSize, chunk.size()),
                                endpointUrl,
                                endpointType));
            } else {
                for (int i = 0; i < (chunk.size() - initialRestMinSize) + 1; i++) {
                    res.add(
                            new Task(
                                    chunk.subList(i * initialRestMinSize, (i + 1) * initialRestMinSize),
                                    endpointUrl,
                                    endpointType));
                }
            }
            return res;
        }

        boolean canSplit() {
            return (chunk.size() / initialRestMinSize) > 0 && chunk.size() != 1;
        }

        int getStatus() {
            return status;
        }

        boolean isSuccess() {
            return status == SC_OK;
        }

        public Map<ProjectVersionRef, String> getResult() {
            return result;
        }

        String getErrorMessage() {
            return (exception != null ? exception.getMessage() + ' ' : "") + (errorString != null ? errorString : "");
        }

        int getChunkSize() {
            return chunk.size();
        }
    }

    private static void printFinishTime(Logger logger, long start, boolean finished) {
        long finish = System.nanoTime();
        long minutes = TimeUnit.NANOSECONDS.toMinutes(finish - start);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(finish - start) - (minutes * 60);
        logger.info(
                "REST client finished {}... (took {} min, {} sec, {} millisec)",
                (finished ? "successfully" : "with failures"),
                minutes,
                seconds,
                (TimeUnit.NANOSECONDS.toMillis(finish - start) - (minutes * 60 * 1000) - (seconds * 1000)));
    }
}
