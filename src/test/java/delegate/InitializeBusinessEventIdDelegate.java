package delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.UUID;

public class InitializeBusinessEventIdDelegate implements JavaDelegate {


   @Override
   public final void execute(DelegateExecution execution) throws Exception {

      String uuidForProcessToCall = UUID.randomUUID().toString();

      execution.setVariable(StartProcessByMessageDelegate.WAIT_FOR_BEID_VARIABLE, uuidForProcessToCall);
   }
}
