package jetbrains.buildserver.agentInfo;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.*;
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
  private static final String FREE_SPACE_REFRESH_TIMEOUT = "teamcity.agent.recalculate.disk.free.space";

  private static final Logger LOG = Logger.getInstance(AgentInfo.class.getName());

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

        service.scheduleWithFixedDelay(action, 0, parseRefreshTimeout(), TimeUnit.SECONDS);
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

  private long parseRefreshTimeout() {
    final long defaultTimeout = 30 * 60;
    try {
      final String value = myConfig.getConfigurationParameters().get(FREE_SPACE_REFRESH_TIMEOUT);
      if (StringUtil.isEmptyOrSpaces(value)) {
        return defaultTimeout;
      }
      int newTime = Integer.parseInt(value.trim());
      if (newTime > 0) {
        return newTime;
      } else {
        throw new RuntimeException("Value must be > 0");
      }
    } catch(Exception e) {
      LOG.warn("Failed to parse " + FREE_SPACE_REFRESH_TIMEOUT + " parameter value. Default value will be used.");
      return defaultTimeout;
    }
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
