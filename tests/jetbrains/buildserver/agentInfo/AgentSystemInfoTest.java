
package jetbrains.buildserver.agentInfo;

import jetbrains.buildServer.agent.impl.BaseAgentSpringTestCase;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 29.12.13
 */
@Test
public class AgentSystemInfoTest extends BaseAgentSpringTestCase {

  // seems like this fails under IBM JDK
  public void testPhysicalMemorySizeMB(){
    AgentSystemInfo agentSystemInfo = new AgentSystemInfo(getBuildAgentConfiguration(), getBuildAgent(), getAgentEvents());
    final Long physicalMemorySizeMB = agentSystemInfo.getPhysicalMemorySizeMB();
    System.out.println("Physical memory size: " + physicalMemorySizeMB + "MB");
    assertTrue(physicalMemorySizeMB != null);
    assertTrue(physicalMemorySizeMB > 0 );
  }
}