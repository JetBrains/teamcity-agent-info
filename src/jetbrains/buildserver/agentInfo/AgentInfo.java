package jetbrains.buildserver.agentInfo;

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.NamedDeamonThreadFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Yegor.Yarko
 *         Date: 10.02.2009
 */
public class AgentInfo extends AgentLifeCycleAdapter {
  @NotNull private final BuildAgentConfiguration myConfig;
  private static final long MB = 1024L * 1024L;

  public AgentInfo(@NotNull final BuildAgentConfiguration config,
                   @NotNull final BuildAgent agent,
                   @NotNull final EventDispatcher<AgentLifeCycleListener> events) {
    myConfig = config;
    final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(new NamedDeamonThreadFactory("agent-info recent updates pool"));

    final Runnable action = ExceptionUtil.catchAll("update free space", new Runnable() {
      public void run() {
        if (agent.isRunning()) return;

        publishFreeSpace();
      }
    });

    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void pluginsLoaded() {
        publishStaticParameters();

        service.scheduleWithFixedDelay(action, 0, 5 * 60, TimeUnit.SECONDS);
      }

      @Override
      public void runnerFinished(@NotNull BuildRunnerContext runner, @NotNull BuildFinishedStatus status) {
        publishFreeSpace();
      }

      @Override
      public void agentShutdown() {
        service.shutdown();
      }
    });
  }
  
  private void publishStaticParameters() {
    final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
      com.sun.management.OperatingSystemMXBean sunBean = ((com.sun.management.OperatingSystemMXBean)operatingSystemMXBean);

      long myPhysicalMemoryInMb = sunBean.getTotalPhysicalMemorySize() / MB;
      if (myPhysicalMemoryInMb > 0){
        myConfig.addConfigurationParameter("teamcity.agent.hardware.memorySizeMb", String.valueOf(myPhysicalMemoryInMb));
      }
    }

    long myAvailableProcessors = operatingSystemMXBean.getAvailableProcessors();
    if (myAvailableProcessors > 0){
      myConfig.addConfigurationParameter("teamcity.agent.hardware.cpuCount", String.valueOf(myAvailableProcessors));
    }
  }


  private void publishFreeSpace() {
    final Long space = FileUtil.getFreeSpace(myConfig.getWorkDirectory());
    if (space != null && space >= 0) {
      final long sizeMb = space / MB;
      myConfig.addConfigurationParameter("teamcity.agent.work.dir.freeSpaceMb", String.valueOf(sizeMb));
    }
  }

}
