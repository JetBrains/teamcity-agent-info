package jetbrains.buildserver.agentInfo;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * @author Yegor.Yarko
 *         Date: 10.02.2009
 */
public class AgentInfo extends AgentLifeCycleAdapter {
  long myPhysicalMemoryInMb = -1;
  private int myAvailableProcessors = -1;

  public AgentInfo(@NotNull final EventDispatcher<AgentLifeCycleListener> disp) {
    disp.addListener(this);
  }
  
  @Override
  public void beforeAgentConfigurationLoaded(@NotNull BuildAgent agent) {
    final BuildAgentConfiguration configuration = agent.getConfiguration();

    init();

    if (myPhysicalMemoryInMb > 0){
      configuration.addCustomProperty(Constants.SYSTEM_PREFIX + "teamcity.agent.hardware.memorySize", (new Long(myPhysicalMemoryInMb)).toString());
    }
    if (myAvailableProcessors > 0){
      configuration.addCustomProperty(Constants.SYSTEM_PREFIX + "teamcity.agent.hardware.cpuCount", (new Long(myAvailableProcessors)).toString());
    }
  }

  private void init() {
    final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
      com.sun.management.OperatingSystemMXBean sunBean = ((com.sun.management.OperatingSystemMXBean)operatingSystemMXBean);
      myPhysicalMemoryInMb = sunBean.getTotalPhysicalMemorySize() / (1024L * 1024L);
    }
    myAvailableProcessors = operatingSystemMXBean.getAvailableProcessors();
  }

}
