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
package org.jboss.pnc.maven_manipulator.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.pnc.maven_manipulator.annotation.ConfigValue;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.model.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;

@Named
@Singleton
public class ConfigIO {
    private final Logger logger = LoggerFactory.getLogger(ConfigIO.class);

    private final static String propertyFileString = "pme.properties";
    private final static String yamlPMEFileString = "pme.yaml";
    private final static String yamlPNCFileString = "pnc.yaml";
    private final static String jsonPNCFileString = "pnc.json";

    @ConfigValue(docIndex = "configuration.html#overview")
    public static final String CONFIG_FILE_PRECEDENCE = "allowConfigFilePrecedence";

    public Properties parse(final String workingDir) throws ManipulationException {
        return parse(new File(workingDir));
    }

    public Properties parse(final File workingDir) throws ManipulationException {
        Properties result = new Properties();
        File propertyFile = new File(workingDir, propertyFileString);
        File yamlPMEFile = new File(workingDir, yamlPMEFileString);
        File yamlPNCFile = new File(workingDir, yamlPNCFileString);
        File jsonPNCFile = new File(workingDir, jsonPNCFileString);

        if (propertyFile.exists() && (yamlPMEFile.exists() || yamlPNCFile.exists() || jsonPNCFile.exists())) {
            throw new ManipulationException("Cannot have both yaml, json and property configuration files.");
        } else if (yamlPMEFile.exists() && yamlPNCFile.exists()) {
            throw new ManipulationException("Cannot have both yaml configuration file formats.");
        } else if ((yamlPMEFile.exists() || yamlPNCFile.exists()) && jsonPNCFile.exists()) {
            throw new ManipulationException("Cannot have yaml and json configuration file formats.");
        }
        if (yamlPMEFile.exists()) {
            result = loadYamlFile(yamlPMEFile);
            logger.debug("Read yaml file containing {}.", result);
        } else if (yamlPNCFile.exists()) {
            result = loadYamlFile(yamlPNCFile);
            logger.debug("Read yaml file containing {}.", result);
        } else if (jsonPNCFile.exists()) {
            try {
                result.putAll(JsonPath.parse(jsonPNCFile).read("$.pme", Map.class));
            } catch (IOException | JsonPathException e) {
                throw new ManipulationException("Caught exception processing JSON file.", e);
            }
            logger.debug("Read json file containing {}.", result);
        } else if (propertyFile.exists()) {
            result = loadPropertiesFile(propertyFile);
            logger.debug("Read properties file containing {}.", result);
        }
        return result;
    }

    private Properties loadYamlFile(final File configFile) throws ManipulationException {
        Properties result = new Properties();
        Representer representer = new Representer(new DumperOptions());

        representer.getPropertyUtils().setSkipMissingProperties(true);
        Yaml yaml = new Yaml(representer);

        try {
            YamlFile yf = yaml.loadAs(new FileInputStream(configFile), YamlFile.class);
            result.putAll(yf.getPme());
        } catch (FileNotFoundException e) {
            throw new ManipulationException("Unable to load yaml file.", e);
        }
        return result;
    }

    private Properties loadPropertiesFile(final File configFile) throws ManipulationException {
        Properties result = new Properties();

        try (InputStream input = new FileInputStream(configFile)) {
            result.load(input);
        } catch (IOException e) {
            throw new ManipulationException("Unable to load properties file.", e);
        }
        return result;
    }
}
