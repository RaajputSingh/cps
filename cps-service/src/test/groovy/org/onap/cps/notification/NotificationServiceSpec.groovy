/*
 * ============LICENSE_START=======================================================
 *  Copyright (c) 2021-2022 Bell Canada.
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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
import org.onap.cps.config.AsyncConfig
import org.onap.cps.event.model.CpsDataUpdatedEvent
import org.spockframework.spring.SpringBean
import org.spockframework.spring.SpringSpy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

@SpringBootTest
@EnableConfigurationProperties
@ContextConfiguration(classes = [NotificationProperties, NotificationService, NotificationErrorHandler, AsyncConfig])
class NotificationServiceSpec extends Specification {

    @SpringBean
    NotificationPublisher mockNotificationPublisher = Mock()
    @SpringBean
    CpsDataUpdatedEventFactory mockCpsDataUpdatedEventFactory = Mock()
    @SpringSpy
    NotificationErrorHandler spyNotificationErrorHandler
    @SpringSpy
    NotificationProperties spyNotificationProperties

    @Autowired
    NotificationService objectUnderTest

    @Shared
    def myDataspacePublishedName = 'my-dataspace-published'
    def myAnchorName = 'my-anchorname'
    def myObservedTimestamp = OffsetDateTime.now()

    def 'Skip sending notification when disabled.'() {
        given: 'notification is disabled'
            spyNotificationProperties.isEnabled() >> false
        when: 'dataUpdatedEvent is received'
            objectUnderTest.processDataUpdatedEvent(myDataspacePublishedName, myAnchorName, myObservedTimestamp, '/', Operation.CREATE)
        then: 'the notification is not sent'
            0 * mockNotificationPublisher.sendNotification(_)
    }

    def 'Send notification when enabled: #scenario.'() {
        given: 'notification is enabled'
            spyNotificationProperties.isEnabled() >> true
        and: 'event factory can create event successfully'
            def cpsDataUpdatedEvent = new CpsDataUpdatedEvent()
            mockCpsDataUpdatedEventFactory.createCpsDataUpdatedEvent(dataspaceName, myAnchorName, myObservedTimestamp,
                    Operation.CREATE) >>
                    cpsDataUpdatedEvent
        when: 'dataUpdatedEvent is received'
            def future = objectUnderTest.processDataUpdatedEvent(dataspaceName, myAnchorName, myObservedTimestamp,
                    '/', Operation.CREATE)
        and: 'wait for async processing to complete'
            future.get(10, TimeUnit.SECONDS)
        then: 'async process completed successfully'
            future.isDone()
        and: 'notification is sent'
            expectedSendNotificationCount * mockNotificationPublisher.sendNotification(cpsDataUpdatedEvent)
        where:
            scenario                               | dataspaceName            || expectedSendNotificationCount
            'dataspace name does not match filter' | 'does-not-match-pattern' || 0
            'dataspace name matches filter'        | myDataspacePublishedName || 1
    }

    def 'Send UPDATE operation when non-root data nodes are changed.'() {
        given: 'notification is enabled'
            spyNotificationProperties.isEnabled() >> true
        and: 'event factory creates event if operation is UPDATE'
            def cpsDataUpdatedEvent = new CpsDataUpdatedEvent()
            mockCpsDataUpdatedEventFactory.createCpsDataUpdatedEvent(myDataspacePublishedName, myAnchorName, myObservedTimestamp,
                    Operation.UPDATE) >> cpsDataUpdatedEvent
        when: 'dataUpdatedEvent is received for non-root xpath'
            def future = objectUnderTest.processDataUpdatedEvent(myDataspacePublishedName, myAnchorName, myObservedTimestamp, '/non-root-node',
                    operation)
        and: 'wait for async processing to complete'
            future.get(10, TimeUnit.SECONDS)
        then: 'async process completed successfully'
            future.isDone()
        and: 'notification is sent'
            1 * mockNotificationPublisher.sendNotification(cpsDataUpdatedEvent)
        where:
            operation << [Operation.CREATE, Operation.UPDATE, Operation.DELETE]
    }

    def 'Send same operation when root nodes are changed.'() {
        given: 'notification is enabled'
            spyNotificationProperties.isEnabled() >> true
        and: 'event factory creates event if operation is #operation'
            def cpsDataUpdatedEvent = new CpsDataUpdatedEvent()
            mockCpsDataUpdatedEventFactory.createCpsDataUpdatedEvent(myDataspacePublishedName, myAnchorName, myObservedTimestamp,
                    operation) >> cpsDataUpdatedEvent
        when: 'dataUpdatedEvent is received for root xpath'
            def future = objectUnderTest.processDataUpdatedEvent(myDataspacePublishedName, myAnchorName, myObservedTimestamp, '/',
                    operation)
        and: 'wait for async processing to complete'
            future.get(10, TimeUnit.SECONDS)
        then: 'async process completed successfully'
            future.isDone()
        and: 'notification is sent'
            1 * mockNotificationPublisher.sendNotification(cpsDataUpdatedEvent)
        where:
            operation << [Operation.CREATE, Operation.UPDATE, Operation.DELETE]
    }


    def 'Error handling in notification service.'() {
        given: 'notification is enabled'
            spyNotificationProperties.isEnabled() >> true
        and: 'event factory can not create event successfully'
            mockCpsDataUpdatedEventFactory.createCpsDataUpdatedEvent(myDataspacePublishedName, myAnchorName,
                    myObservedTimestamp, Operation.CREATE) >>
                    { throw new Exception("Could not create event") }
        when: 'event is sent for processing'
            def future = objectUnderTest.processDataUpdatedEvent(myDataspacePublishedName, myAnchorName,
                    myObservedTimestamp, '/', Operation.CREATE)
        and: 'wait for async processing to complete'
            future.get(10, TimeUnit.SECONDS)
        then: 'async process completed successfully'
            future.isDone()
        and: 'error is handled and not thrown to caller'
            notThrown Exception
            1 * spyNotificationErrorHandler.onException(_, _, _, _)
    }

}
