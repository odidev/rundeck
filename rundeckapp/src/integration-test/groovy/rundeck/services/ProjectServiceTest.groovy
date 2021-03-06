/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rundeck.services

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import rundeck.Execution
import spock.lang.Specification

import static org.junit.Assert.*

/**
 * Created by greg on 6/18/15.
 */
@Integration
@Rollback
class ProjectServiceTest extends Specification {

    ProjectService projectService

    def sessionFactory


    static String EXECS_START='<executions>'
    static String EXEC_XML_TEST1_DEF_START= '''
  <execution id='1'>
    <dateStarted>1970-01-01T00:00:00Z</dateStarted>
    <dateCompleted>1970-01-01T01:00:00Z</dateCompleted>
    <status>true</status>'''
    static String EXEC_XML_TEST1_START = EXECS_START+EXEC_XML_TEST1_DEF_START
    static String EXEC_XML_TEST1_DEF_END= '''
    <failedNodeList />
    <succeededNodeList />
    <abortedby />
    <cancelled>false</cancelled>
    <argString>-test args</argString>
    <loglevel>WARN</loglevel>
    <doNodedispatch>true</doNodedispatch>
    <nodefilters>
      <dispatch>
        <threadcount>1</threadcount>
        <keepgoing>false</keepgoing>
        <excludePrecedence>true</excludePrecedence>
        <rankOrder>ascending</rankOrder>
      </dispatch>
      <filter>hostname: test1 !tags: monkey</filter>
    </nodefilters>
    <project>testproj</project>
    <user>testuser</user>
    <workflow keepgoing='false' strategy='node-first'>
      <command>
        <exec>exec command</exec>
      </command>
    </workflow>
  </execution>
'''
    /**
     * Execution xml with associated job ID
     */
    static String EXEC_XML_TEST4 = EXEC_XML_TEST1_START + '''
    <outputfilepath>output-1.rdlog</outputfilepath>''' + '''
    <failedNodeList />
    <succeededNodeList />
    <abortedby />
    <cancelled>false</cancelled>
    <argString>-test args</argString>
    <loglevel>WARN</loglevel>
    <doNodedispatch>true</doNodedispatch>
    <nodefilters>
      <dispatch>
        <threadcount>1</threadcount>
        <keepgoing>false</keepgoing>
        <excludePrecedence>true</excludePrecedence>
        <rankOrder>ascending</rankOrder>
      </dispatch>
      <filter>hostname: test1 !tags: monkey</filter>
    </nodefilters>
    <project>testproj</project>
    <user>testuser</user>
    <workflow keepgoing='false' strategy='node-first'>
      <command>
        <jobref name='echo' nodeStep='true'>
          <arg line='-name ${node.name}' />
        </jobref>
        <description>echo on node</description>
      </command>
    </workflow>
  </execution>
</executions>''' /**
     * Execution xml with orchestrator
     */
    static String EXEC_XML_TEST5 = EXEC_XML_TEST1_START + '''
    <outputfilepath>output-1.rdlog</outputfilepath>''' + '''
    <failedNodeList />
    <succeededNodeList />
    <abortedby />
    <cancelled>false</cancelled>
    <argString>-test args</argString>
    <loglevel>WARN</loglevel>
    <doNodedispatch>true</doNodedispatch>
    <nodefilters>
      <dispatch>
        <threadcount>1</threadcount>
        <keepgoing>false</keepgoing>
        <excludePrecedence>true</excludePrecedence>
        <rankOrder>ascending</rankOrder>
      </dispatch>
      <filter>hostname: test1 !tags: monkey</filter>
    </nodefilters>
    <project>testproj</project>
    <user>testuser</user>
    <workflow keepgoing='false' strategy='node-first'>
      <command>
        <jobref name='echo' nodeStep='true'>
          <arg line='-name ${node.name}' />
        </jobref>
        <description>echo on node</description>
      </command>
    </workflow>

    <orchestrator>
      <type>subset</type>
      <configuration>
        <count>1</count>
      </configuration>
    </orchestrator>
  </execution>
</executions>'''

    /**
     * import executions with orchestrator definition
     */

    void  "testImportExecutionsToProject_Workflow_noOutfile"(){\
        when:
        def temp = File.createTempFile("execxml",".tmp")
        temp.text=EXEC_XML_TEST4
        temp.deleteOnExit()
        def errs=[]
        def result

            result  = projectService.importExecutionsToProject([temp], [:], "test", null, [:], [], [(temp): "temp-xml-file"], errs)
            sessionFactory.currentSession.flush()

        then:
        assertEquals("expected no errors but saw: ${errs}", 1,errs.size())
        assertTrue(errs[0].contains("NO matching outfile"))
    }
    /**
     * import executions with retryExecution link
     */

    void  "testImportExecutionsToProject_retryId"(){
        when:
        def id2 = 13L
        def id1 = 1L
        def temp = File.createTempFile("execxml",".tmp")
        temp.text = """
<executions>
      <execution id='${id2}'>
        <dateStarted>1970-01-01T00:00:00Z</dateStarted>
        <dateCompleted>1970-01-01T01:00:00Z</dateCompleted>
        <status>true</status><outputfilepath />""" + EXEC_XML_TEST1_DEF_END + "</executions>"

        temp.deleteOnExit()

        def temp2 = File.createTempFile("execxml2",".tmp")
        temp2.text = EXECS_START + EXEC_XML_TEST1_DEF_START +
                '''<retryExecutionId>13</retryExecutionId> <outputfilepath />''' +
                EXEC_XML_TEST1_DEF_END +
                "</executions>"

        temp2.deleteOnExit()
        def errs=[]
        Map result
            result  = projectService.importExecutionsToProject([temp,temp2], [:], "test", null, [:], [], [(temp): "temp-xml-file"], errs)
            sessionFactory.currentSession.flush()
        def exec = Execution.get(result[(int) id1])
        def exec2 = Execution.get(result[(int) id2])

        then:
        assertEquals("expected no errors but saw: ${errs}", 2,errs.size())
        assertTrue(errs[0].contains("NO matching outfile"))
        assertTrue(errs[1].contains("NO matching outfile"))
        assertEquals(2,result.size())

        assertNotNull(result[(int)id1])
        assertNotNull(result[(int)id2])

            assertNotNull(Execution.get(result[(int) id1]))
            assertNotNull(Execution.get(result[(int) id2]))

            assertNotNull(exec.retryExecution)

            assertEquals(exec2.id, exec.retryExecution.id)
    }
    /**
     * import executions with orchestrator definition
     */
    void  "testImportExecutionsToProject_Workflow_withOutfile"(){
        when:
        def temp = File.createTempFile("execxml",".tmp")
        temp.text=EXEC_XML_TEST4
        temp.deleteOnExit()
        def outfile = File.createTempFile("output",".tmp")
        outfile.text="bah"
        outfile.deleteOnExit()
        def resultoutfile = File.createTempFile("newoutput",".tmp")
        resultoutfile.deleteOnExit()
        assertTrue resultoutfile.delete()
        def errs=[]
        def result
        projectService.logFileStorageService.metaClass.getFileForExecutionFiletype={Execution execution, String filetype, boolean useStoredPath, boolean partial->
            resultoutfile
        }
            result  = projectService.importExecutionsToProject([temp], ["output-1.rdlog":outfile], "test", null, [:], [], [(temp): "temp-xml-file"], errs)
            sessionFactory.currentSession.flush()
        resultoutfile.deleteOnExit()
        then:
        assertEquals("expected no errors but saw: ${errs}", 0,errs.size())
        assertEquals("bah",resultoutfile.text)

    }
    /**
     * import executions with orchestrator definition
     */
    void  "testImportExecutionsToProject_Workflow_withStatefile"(){
        when:
        def temp = File.createTempFile("execxml",".tmp")
        temp.text=EXEC_XML_TEST4
        temp.deleteOnExit()
        def outfile = File.createTempFile("output",".tmp")
        outfile.text="bah"
        outfile.deleteOnExit()
        def statefile = File.createTempFile("statefile",".tmp")
        statefile.text="state file contents"
        statefile.deleteOnExit()
        def resultoutfile = File.createTempFile("newoutput",".tmp")
        resultoutfile.deleteOnExit()
        assertTrue resultoutfile.delete()
        def resultstatefile = File.createTempFile("newstate",".tmp")
        resultstatefile.deleteOnExit()
        assertTrue resultstatefile.delete()
        def files = [
                "state.json":resultstatefile,
                "rdlog":resultoutfile
        ]
        def errs=[]
        def result
        projectService.logFileStorageService.metaClass.getFileForExecutionFiletype={Execution execution, String filetype, boolean useStoredPath, boolean partial->
            files[filetype]
        }
            result  = projectService.importExecutionsToProject([temp], ["output-1.rdlog":outfile,"state-1.state.json":statefile], "test", null, [:], [], [(temp): "temp-xml-file"], errs)
            sessionFactory.currentSession.flush()

        then:
        assertEquals("expected no errors but saw: ${errs}", 0,errs.size())
        assertEquals("bah",resultoutfile.text)
        assertEquals("state file contents",resultstatefile.text)

        cleanup:
        resultoutfile.deleteOnExit()
        resultstatefile.deleteOnExit()
    }
    /**
     * import executions with orchestrator definition
     */
    void  "testImportExecutionsToProject_Orchestrator"(){
        when:
        def temp = File.createTempFile("execxml",".tmp")
        temp.text=EXEC_XML_TEST5
        temp.deleteOnExit()
        def errs=[]
        def result = projectService.importExecutionsToProject([temp], [:], "test", null, [:], [], [(temp): "temp-xml-file"], errs)
        sessionFactory.currentSession.flush()
        then:
        assertEquals("expected no errors but saw: ${errs}", 1,errs.size())
        assertTrue(errs[0].contains("NO matching outfile"))
    }
}
