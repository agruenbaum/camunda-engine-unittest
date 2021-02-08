package delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;

import java.util.UUID;

public class StartProcessByMessageDelegate implements JavaDelegate {

   public static final String PROCESS_TO_START_VARIABLE = "PROCESS_TO_START";
   public static final String WAIT_FOR_BEID_VARIABLE = "WAIT_FOR_BEID";

   @Override
   public final void execute(DelegateExecution execution) throws Exception {

      if(!execution.hasVariable(PROCESS_TO_START_VARIABLE)){
         throw new IllegalStateException("Process has no Variable " + PROCESS_TO_START_VARIABLE + " therefore no Process can be started");
      }

      String uuidForProcessToCall;
      if (execution.hasVariable(WAIT_FOR_BEID_VARIABLE)){
         uuidForProcessToCall = (String) execution.getVariable(WAIT_FOR_BEID_VARIABLE); //some tests need to define that id before that step
      } else {
         uuidForProcessToCall = UUID.randomUUID().toString();
      }

      ProcessInstance processInstance = execution.getProcessEngine().getRuntimeService().startProcessInstanceByKey((String) execution.getVariable(PROCESS_TO_START_VARIABLE), uuidForProcessToCall);

      execution.setVariable(WAIT_FOR_BEID_VARIABLE, processInstance.getBusinessKey());
   }
}
