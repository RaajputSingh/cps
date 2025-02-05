/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021-2022 Nordix Foundation
 *  Modifications Copyright (C) 2021-2022 Bell Canada.
 *  Modifications Copyright (C) 2021 Pantheon.tech
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.onap.cps.rest.controller

import static org.onap.cps.spi.FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS
import static org.onap.cps.spi.FetchDescendantsOption.OMIT_DESCENDANTS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

import com.fasterxml.jackson.databind.ObjectMapper
import org.onap.cps.utils.JsonObjectMapper
import org.onap.cps.api.CpsQueryService
import org.onap.cps.spi.model.DataNodeBuilder
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

@WebMvcTest(QueryRestController)
class QueryRestControllerSpec extends Specification {

    @SpringBean
    CpsQueryService mockCpsQueryService = Mock()

    @SpringBean
    JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper())

    @Autowired
    MockMvc mvc

    @Value('${rest.api.cps-base-path}')
    def basePath

    def 'Query data node by cps path for the given dataspace and anchor with #scenario.'() {
        given: 'service method returns a list containing a data node'
             def dataNode1 = new DataNodeBuilder().withXpath('/xpath')
                    .withLeaves([leaf: 'value', leafList: ['leaveListElement1', 'leaveListElement2']]).build()
            def dataspaceName = 'my_dataspace'
            def anchorName = 'my_anchor'
            def cpsPath = 'some cps-path'
            mockCpsQueryService.queryDataNodes(dataspaceName, anchorName, cpsPath, expectedCpsDataServiceOption) >> [dataNode1, dataNode1]
        and: 'the query endpoint'
            def dataNodeEndpoint = "$basePath/v1/dataspaces/$dataspaceName/anchors/$anchorName/nodes/query"
        when: 'query data nodes API is invoked'
            def response =
                    mvc.perform(
                            get(dataNodeEndpoint)
                                    .param('cps-path', cpsPath)
                                    .param('include-descendants', includeDescendantsOption))
                            .andReturn().response
        then: 'the response contains the the datanode in json format'
            response.status == HttpStatus.OK.value()
            response.getContentAsString().contains('{"xpath":{"leaf":"value","leafList":["leaveListElement1","leaveListElement2"]}}')
        where: 'the following options for include descendants are provided in the request'
            scenario                    | includeDescendantsOption || expectedCpsDataServiceOption
            'no descendants by default' | ''                       || OMIT_DESCENDANTS
            'no descendant explicitly'  | 'false'                  || OMIT_DESCENDANTS
            'descendants'               | 'true'                   || INCLUDE_ALL_DESCENDANTS
    }
}
