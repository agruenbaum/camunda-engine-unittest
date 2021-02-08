package org.camunda.bpm.unittest;

import delegate.StartProcessByMessageDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;

public class SignalCommunicationUseCaseTest {

   @Rule
   public ProcessEngineRule rule = new ProcessEngineRule();


   /**
    * Just tests if the process definition is deployable.
    */
   @Test
   @Deployment(resources = {"TestProcessWaitForSignal.bpmn",
         "TestProcessWaitForSignalParallel.bpmn",
         "TestProcessSendSignal.bpmn",
         "TestProcessSendSignalImmediately.bpmn",
         "TestProcessSendSignalAfterTx.bpmn"
        })
   public void testParsingAndDeployment() { //NOSONAR no assertion needed to test deployment
      // nothing is done here, as we just want to check for exceptions during deployment
   }


   /**
    * UseCase:
    * TestProcessWaitForSignal (process one) is startet and then starts the process TestProcessSendSignal (process two) by message
    * The Process two creates a UserTask and waits.
    * The Process one runs into a signal receive waitState and waits for the termination signal of process two.
    * The signal he waits for is named "processfinished-${WAIT_FOR_BEID}" where ${WAIT_FOR_BEID} stands for the BEID of process two.
    *
    * After closing the user task in process two he sends a signal processfinished-${execution.processBusinessKey}
    * As this is the signal that process one waits for it will be executed and finished.
    *
    *
    * Testing SignalPayload:
    * The Signal sent in process two will transport the value of the process variable myTaskInput in the new variable myPayload.
    * the myTaskInput variable will be defined when completing the usertask in process two.
    */
   @Test
   @Deployment(resources = {
         "TestProcessWaitForSignal.bpmn",
         "TestProcessSendSignal.bpmn"
   })
   public void testProcessNotificationByProcessSpecificSignal()  {
      //GIVEN
      String processDefinitionKey = "TestProcessWaitForSignal";

      String processToStart = "TestProcessSendSignal";
      HashMap<String, Object> startVariables = new HashMap<>();
      startVariables.put(StartProcessByMessageDelegate.PROCESS_TO_START_VARIABLE, processToStart);
      //WHEN
      ProcessInstance initialProcessInstance = runtimeService().startProcessInstanceByKey(processDefinitionKey, UUID.randomUUID().toString(),startVariables);


      //THEN
      assertThat(initialProcessInstance).isNotEnded();

      ProcessInstance processInstanceStartedByMsg = runtimeService().createProcessInstanceQuery().processDefinitionKey(processToStart).singleResult();
      assertThat(processInstanceStartedByMsg).isNotEnded();


      HashMap<String, Object> taskVariables = new HashMap<>();
      String userInput = "TaskInputPayloadForSignal";
      taskVariables.put("myTaskInput", userInput );
      complete(task(processInstanceStartedByMsg), taskVariables);

      assertThat(initialProcessInstance).isEnded().hasPassed("Event_EndProcess");
      assertThat(initialProcessInstance).variables().containsEntry("myPayload", userInput);
      assertThat(initialProcessInstance).variables().containsEntry(StartProcessByMessageDelegate.WAIT_FOR_BEID_VARIABLE, processInstanceStartedByMsg.getBusinessKey());

      assertThat(processInstanceStartedByMsg).isEnded();
   }


   /**
    * UseCase:
    * Like the testProcessNotificationByProcessSpecificSignal but the second started process has no waitstate and returns immediately.
    *
    * Attention: this case (which could happen if you don't have a transaction boundary or a usertask in the called process is a DEADLOCK.
    *
    * The process two sends the signal before the process one starts to wait for the signal.
    * Therefore the process one misses the signal and then waits forever for the signal.
    */
   @Test
   @Deployment(resources = {
         "TestProcessWaitForSignal.bpmn",
         "TestProcessSendSignalImmediately.bpmn"
   })
   public void currentlyFailingTest_testProcessNotificationByProcessSpecificSignal_withoutInterruption() {
      //GIVEN
      String processDefinitionKey = "TestProcessWaitForSignal";

      String processToStart = "TestProcessSendSignalImmediately";

      HashMap<String, Object> startVariables = new HashMap<>();
      startVariables.put(StartProcessByMessageDelegate.PROCESS_TO_START_VARIABLE, processToStart);

      //WHEN
      ProcessInstance initialProcessInstance = runtimeService().startProcessInstanceByKey(processDefinitionKey, UUID.randomUUID().toString(),startVariables);


      //THEN
      ProcessInstance processInstanceStartedByMsg = runtimeService().createProcessInstanceQuery().processDefinitionKey(processToStart).singleResult();

      assertThat(initialProcessInstance).isEnded().hasPassed("Event_EndProcess");
      assertThat(initialProcessInstance).variables().containsEntry(StartProcessByMessageDelegate.WAIT_FOR_BEID_VARIABLE, processInstanceStartedByMsg.getBusinessKey());

      assertThat(processInstanceStartedByMsg).isEnded();
   }

   /**
    * UseCase:
    * Like the testProcessNotificationByProcessSpecificSignal_withoutInterruption but the second startet process has a transaction boundary set at the process start.
    *
    * therefore the process one can reach the signal waitstate before the process two will continue in a second transaction.
    *
    * here we don't test the payload but that would work the same way as in test one.
    */
   @Test
   @Deployment(resources = {
         "TestProcessWaitForSignal.bpmn",
         "TestProcessSendSignalAfterTx.bpmn"
   })
   public void testProcessNotificationByProcessSpecificSignal_withTransaction() {
      //GIVEN
      String processDefinitionKey = "TestProcessWaitForSignal";

      String processToStart = "TestProcessSendSignalAfterTx";

      HashMap<String, Object> startVariables = new HashMap<>();
      startVariables.put(StartProcessByMessageDelegate.PROCESS_TO_START_VARIABLE, processToStart);

      //WHEN
      ProcessInstance initialProcessInstance = runtimeService().startProcessInstanceByKey(processDefinitionKey, UUID.randomUUID().toString(),startVariables);

      //THEN
      assertThat(initialProcessInstance).isNotEnded();

      ProcessInstance processInstanceStartedByMsg = runtimeService().createProcessInstanceQuery().processDefinitionKey(processToStart).singleResult();
      assertThat(processInstanceStartedByMsg).isNotEnded();


      //execute the job -> the process two will end immediately and send a signal to process one which will end as well...
      execute(job());

      assertThat(initialProcessInstance).isEnded().hasPassed("Event_EndProcess");
      assertThat(initialProcessInstance).variables().containsEntry(StartProcessByMessageDelegate.WAIT_FOR_BEID_VARIABLE, processInstanceStartedByMsg.getBusinessKey());

      assertThat(processInstanceStartedByMsg).isEnded();
   }

   /**
    * UseCase:
    * Like the testProcessNotificationByProcessSpecificSignal_withoutInterruption but the first started process already starts waiting for the signal in paralell while he starts the second process.
    *
    * Therefore he should be able to receive the signal.
    *
    * Attention: this case (which could happen if you don't have a transaction boundary or a usertask in the called process is a DEADLOCK.
    *
    * The process two sends the signal before the process one starts to wait for the signal.
    * Therefore the process one misses the signal and then waits forever for the signal.
    */
   @Test
   @Deployment(resources = {
         "TestProcessWaitForSignalParallel.bpmn",
         "TestProcessSendSignalImmediately.bpmn"
   })
   public void currentlyFailingTest_testProcessNotificationByProcessSpecificSignalParallel_withoutInterruption(){
      //GIVEN
      String processDefinitionKey = "TestProcessWaitForSignalParallel";

      String processToStart = "TestProcessSendSignalImmediately";

      HashMap<String, Object> startVariables = new HashMap<>();
      startVariables.put(StartProcessByMessageDelegate.PROCESS_TO_START_VARIABLE, processToStart);

      //WHEN
      ProcessInstance initialProcessInstance = runtimeService().startProcessInstanceByKey(processDefinitionKey, UUID.randomUUID().toString(),startVariables);

      //THEN
      assertThat(initialProcessInstance).isNotEnded();

      ProcessInstance processInstanceStartedByMsg = runtimeService().createProcessInstanceQuery().processDefinitionKey(processToStart).singleResult();
      assertThat(processInstanceStartedByMsg).isNotEnded();

      assertThat(initialProcessInstance).isEnded().hasPassed("Event_EndProcess");
      assertThat(initialProcessInstance).variables().containsEntry(StartProcessByMessageDelegate.WAIT_FOR_BEID_VARIABLE, processInstanceStartedByMsg.getBusinessKey());

      assertThat(processInstanceStartedByMsg).isEnded();
   }
}
