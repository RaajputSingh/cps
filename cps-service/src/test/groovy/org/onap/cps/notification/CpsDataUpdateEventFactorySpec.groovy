/*
 * ============LICENSE_START=======================================================
 * Copyright (c) 2021-2022 Bell Canada.
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

package org.onap.cps.notification

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.onap.cps.utils.DateTimeUtility
import org.onap.cps.api.CpsAdminService
import org.onap.cps.api.CpsDataService
import org.onap.cps.event.model.Content
import org.onap.cps.event.model.Data
import org.onap.cps.spi.FetchDescendantsOption
import org.onap.cps.spi.model.Anchor
import org.onap.cps.spi.model.DataNodeBuilder
import org.springframework.util.StringUtils
import spock.lang.Specification

class CpsDataUpdateEventFactorySpec extends Specification {

    def mockCpsDataService = Mock(CpsDataService)
    def mockCpsAdminService = Mock(CpsAdminService)

    def objectUnderTest = new CpsDataUpdatedEventFactory(mockCpsDataService, mockCpsAdminService)

    def myDataspaceName = 'my-dataspace'
    def myAnchorName = 'my-anchorname'
    def mySchemasetName = 'my-schemaset-name'
    def dateTimeFormat = 'yyyy-MM-dd\'T\'HH:mm:ss.SSSZ'

    def 'Create a CPS data updated event successfully: #scenario'() {
        given: 'cps admin service is able to return anchor details'
            mockCpsAdminService.getAnchor(myDataspaceName, myAnchorName) >>
                new Anchor(myAnchorName, myDataspaceName, mySchemasetName)
        and: 'cps data service returns the data node details'
            def xpath = '/'
            def dataNode = new DataNodeBuilder().withXpath(xpath).withLeaves(['leafName': 'leafValue']).build()
            mockCpsDataService.getDataNode(
                myDataspaceName, myAnchorName, xpath, FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS) >> dataNode
        when: 'CPS data updated event is created'
            def cpsDataUpdatedEvent = objectUnderTest.createCpsDataUpdatedEvent(myDataspaceName,
                myAnchorName, DateTimeUtility.toOffsetDateTime(inputObservedTimestamp), Operation.CREATE)
        then: 'CPS data updated event is created with correct envelope'
            with(cpsDataUpdatedEvent) {
                type == 'org.onap.cps.data-updated-event'
                source == new URI('urn:cps:org.onap.cps')
                schema == new URI('urn:cps:org.onap.cps:data-updated-event-schema:v1')
                StringUtils.hasText(id)
                content != null
            }
        and: 'correct content'
            with(cpsDataUpdatedEvent.content) {
                assert isExpectedDateTimeFormat(observedTimestamp): "$observedTimestamp is not in $dateTimeFormat format"
                if (inputObservedTimestamp != null)
                    assert observedTimestamp == inputObservedTimestamp
                else
                    assert OffsetDateTime.now().minusSeconds(20).isBefore(
                        DateTimeUtility.toOffsetDateTime(observedTimestamp))
                assert anchorName == myAnchorName
                assert dataspaceName == myDataspaceName
                assert schemaSetName == mySchemasetName
                assert operation == Content.Operation.CREATE
                assert data == new Data().withAdditionalProperty('leafName', 'leafValue')
            }
        where:
            scenario                        | inputObservedTimestamp
            'with observed timestamp -0400' | '2021-01-01T23:00:00.345-0400'
            'with observed timestamp +0400' | '2021-01-01T23:00:00.345+0400'
            'missing observed timestamp'    | null
    }

    def isExpectedDateTimeFormat(String observedTimestamp) {
        try {
            DateTimeFormatter.ofPattern(dateTimeFormat).parse(observedTimestamp)
        } catch (DateTimeParseException) {
            return false
        }
        return true
    }

}
