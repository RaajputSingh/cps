/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Nordix Foundation
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

package org.onap.cps.cpspath.parser

import spock.lang.Specification

import static org.onap.cps.cpspath.parser.CpsPathPrefixType.ABSOLUTE
import static org.onap.cps.cpspath.parser.CpsPathPrefixType.DESCENDANT

class CpsPathQuerySpec extends Specification {

    def 'Parse cps path with valid cps path and a filter with #scenario.'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom(cpsPath)
        then: 'the query has the right xpath type'
            result.cpsPathPrefixType == ABSOLUTE
        and: 'the right query parameters are set'
            result.xpathPrefix == expectedXpathPrefix
            result.hasLeafConditions() == true
            result.leavesData.containsKey(expectedLeafName) == true
            result.leavesData.get(expectedLeafName) == expectedLeafValue
        where: 'the following data is used'
            scenario               | cpsPath                                                    || expectedXpathPrefix                         | expectedLeafName       | expectedLeafValue
            'leaf of type String'  | '/parent/child[@common-leaf-name="common-leaf-value"]'     || '/parent/child'                             | 'common-leaf-name'     | 'common-leaf-value'
            'leaf of type String'  | '/parent/child[@common-leaf-name=\'common-leaf-value\']'   || '/parent/child'                             | 'common-leaf-name'     | 'common-leaf-value'
            'leaf of type Integer' | '/parent/child[@common-leaf-name-int=5]'                   || '/parent/child'                             | 'common-leaf-name-int' | 5
            'spaces around ='      | '/parent/child[@common-leaf-name-int = 5]'                 || '/parent/child'                             | 'common-leaf-name-int' | 5
            'key in top container' | '/parent[@common-leaf-name-int=5]'                         || '/parent'                                   | 'common-leaf-name-int' | 5
            'parent list'          | '/shops/shop[@id=1]/categories[@id=1]/book[@title="Dune"]' || '/shops/shop[@id=1]/categories[@id=1]/book' | 'title'                | 'Dune'
    }

    def 'Parse cps path of type ends with a #scenario.'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom(cpsPath)
        then: 'the query has the right xpath type'
            result.cpsPathPrefixType == DESCENDANT
        and: 'the right ends with parameters are set'
            result.descendantName == expectedDescendantName
        where: 'the following data is used'
            scenario         | cpsPath          || expectedDescendantName
            'yang container' | '//cps-path'     || 'cps-path'
            'parent & child' | '//parent/child' || 'parent/child'
    }

    def 'Parse cps path that ends with a yang list containing #scenario.'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom(cpsPath)
        then: 'the query has the right xpath type'
            result.cpsPathPrefixType == DESCENDANT
        and: 'the right parameters are set'
            result.descendantName == "child"
            result.leavesData.size() == expectedNumberOfLeaves
        where: 'the following data is used'
            scenario                  | cpsPath                                            || expectedNumberOfLeaves
            'one attribute'           | '//child[@common-leaf-name-int=5]'                 || 1
            'more than one attribute' | '//child[@int-leaf=5 and @leaf-name="leaf value"]' || 2
    }

    def 'Parse #scenario cps path with text function condition'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom(cpsPath)
        then: 'the query has the right xpath type'
            result.cpsPathPrefixType == DESCENDANT
        and: 'leaf conditions are only present when expected'
            result.hasLeafConditions() == expectLeafConditions
        and: 'the right text function condition is set'
            result.hasTextFunctionCondition()
            result.textFunctionConditionLeafName == 'leaf-name'
            result.textFunctionConditionValue == 'search'
        and: 'the ancestor is only present when expected'
            assert result.hasAncestorAxis() == expectHasAncestorAxis
        where: 'the following data is used'
            scenario                                  | cpsPath                                                              || expectLeafConditions | expectHasAncestorAxis
            'descendant anywhere'                     | '//someContainer/leaf-name[text()="search"]'                         || false                | false
            'descendant with leaf value'              | '//child[@other-leaf=1]/leaf-name[text()="search"]'                  || true                 | false
            'descendant anywhere and ancestor'        | '//someContainer/leaf-name[text()="search"]/ancestor::parent'        || false                | true
            'descendant with leaf value and ancestor' | '//child[@other-leaf=1]/leaf-name[text()="search"]/ancestor::parent' || true                 | true
    }

    def 'Parse cps path with error: #scenario.'() {
        when: 'the given cps path is parsed'
            CpsPathQuery.createFrom(cpsPath)
        then: 'a CpsPathException is thrown'
            thrown(IllegalStateException)
        where: 'the following data is used'
            scenario                                                            | cpsPath
            'no / at the start'                                                 | 'invalid-cps-path/child'
            'additional / after descendant option'                              | '///cps-path'
            'float value'                                                       | '/parent/child[@someFloat=5.0]'
            'unmatched quotes, double quote first '                             | '/parent/child[@someString="value with unmatched quotes\']'
            'unmatched quotes, single quote first'                              | '/parent/child[@someString=\'value with unmatched quotes"]'
            'end with descendant and more than one attribute separated by "or"' | '//child[@int-leaf=5 or @leaf-name="leaf value"]'
            'missing attribute value'                                           | '//child[@int-leaf=5 and @name]'
            'incomplete ancestor value'                                         | '//books/ancestor::'
//  DISCUSS WITH TEAM :           'unsupported postfix after value condition (JIRA CPS-450)'          | '/parent/child[@id=1]/somePostFix'
    }

    def 'Parse cps path using ancestor by schema node identifier with a #scenario.'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom('//descendant/ancestor::' + ancestorPath)
        then: 'the query has the right type'
            result.cpsPathPrefixType == DESCENDANT
        and: 'the result has ancestor axis'
            result.hasAncestorAxis()
        and: 'the correct ancestor schema node identifier is set'
            result.ancestorSchemaNodeIdentifier == ancestorPath
        and: 'there are no leaves conditions'
            result.hasLeafConditions() == false
        where:
            scenario                  | ancestorPath
            'basic container'         | 'someContainer'
            'container with parent'   | 'parent/child'
            'ancestor that is a list' | 'categories[@code=1]'
            'parent that is a list'   | 'parent[@id=1]/child'
    }

    def 'Combinations #scenario.'() {
        when: 'the given cps path is parsed'
            def result = CpsPathQuery.createFrom(cpsPath + '/ancestor::someAncestor')
        then: 'the query has the right type'
            result.cpsPathPrefixType == DESCENDANT
        and: 'leaf conditions are only present when expected'
            result.hasLeafConditions() == expectLeafConditions
        and: 'the result has ancestor axis'
            result.hasAncestorAxis()
        and: 'the correct ancestor schema node identifier is set'
            result.ancestorSchemaNodeIdentifier == 'someAncestor'
            result.descendantName == expectedDescendantName
        where:
            scenario                     | cpsPath                               || expectedDescendantName | expectLeafConditions
            'basic container'            | '//someContainer'                     || 'someContainer'        | false
            'container with parent'      | '//parent/child'                      || 'parent/child'         | false
            'container with list-parent' | '//parent[@id=1]/child'               || 'parent[@id=1]/child'  | false
            'container with list-parent' | '//parent[@id=1]/child[@name="test"]' || 'parent[@id=1]/child'  | true
    }

}
