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

/**
 * This package contains the JSON POJOs objects that PME uses.
 *
 * A typical output report may look like
 *
 * <pre>
 *{
 *  "executionRoot" : {
 *    "groupId" : "io.vertx",
 *    "artifactId" : "vertx-core",
 *    "version" : "3.3.3.temporary-redhat-00001",
 *    "originalGAV" : "io.vertx:vertx-core:3.3.3"
 *  },
 *  "modules" : [ {
 *    "gav" : {
 *      "groupId" : "io.vertx",
 *      "artifactId" : "vertx-core",
 *      "version" : "3.3.3.temporary-redhat-00001",
 *      "originalGAV" : "io.vertx:vertx-core:3.3.3"
 *    },
 *    "properties" : {
 *      "log4j2.version" : {
 *        "oldValue" : "2.5",
 *        "value" : "2.5.0.redhat-3"
 *      }
 *    },
 *    "managedPlugins" : {
 *      "plugins" : {
 *        "io.thorntail:thorntail-maven-plugin:2.4.0.Final" : {
 *          "groupId" : "io.thorntail",
 *          "artifactId" : "thorntail-maven-plugin",
 *          "version" : "2.4.0.Final-temporary-redhat-00001"
 *        }
 *      }
 *    },
 *    "managedDependencies" : {
 *      "dependencies" : {
 *        "io.vertx:vertx-dependencies:3.3.3" : {
 *          "groupId" : "io.vertx",
 *          "artifactId" : "vertx-dependencies",
 *          "version" : "3.3.3.redhat-3"
 *        }
 *      }
 *    },
 *    "dependencies" : {
 *      "io.netty:netty-common:4.1.5.Final" : {
 *        "groupId" : "io.netty",
 *        "artifactId" : "netty-common",
 *        "version" : "4.1.5.Final-redhat-2"
 *      },
 *      "io.netty:netty-resolver:4.1.5.Final" : {
 *        "groupId" : "io.netty",
 *        "artifactId" : "netty-resolver",
 *        "version" : "4.1.5.Final-redhat-2"
 *      }
 *   }
 *  } ]
 *}
 * </pre>
 *
 *
 */
package org.jboss.pnc.mavenmanipulator.common.json;
