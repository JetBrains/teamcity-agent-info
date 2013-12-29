package jetbrains.buildserver.agentInfo;

import com.intellij.openapi.diagnostic.Logger;
import javax.management.*;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Yegor.Yarko
 *         Date: 10.02.2009
 */
public class AgentSystemInfo extends AgentLifeCycleAdapter {
  private static final String FREE_SPACE_REFRESH_TIMEOUT = "teamcity.agent.recalculate.disk.free.space";

  private static final Logger LOG = Logger.getInstance(AgentSystemInfo.class.getName());

  @NotNull private final BuildAgentConfiguration myConfig;
  private static final long MB = 1024L * 1024L;
  private static final String PHYSICAL_MEMORY_KEY = "teamcity.agent.hardware.memorySizeMb";
  private static final String PROCESSORS_COUNT_KEY = "teamcity.agent.hardware.cpuCount";
  private static final String FREE_SPACE_KEY = "teamcity.agent.work.dir.freeSpaceMb";

  public AgentSystemInfo(@NotNull final BuildAgentConfiguration config,
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


  @Nullable
  Long getPhysicalMemorySizeMB() {
    try {
      final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
      if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
        com.sun.management.OperatingSystemMXBean sunBean = ((com.sun.management.OperatingSystemMXBean)operatingSystemMXBean);

        long myPhysicalMemoryInMb = sunBean.getTotalPhysicalMemorySize() / MB;
        if (myPhysicalMemoryInMb > 0) {
          return myPhysicalMemoryInMb;
        }
      }
    } catch (Exception e) {
      LOG.info("Class 'com.sun.management.OperatingSystemMXBean' not found or another error, using alternative way to get total memory.");
      try {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Object attribute = mBeanServer.getAttribute(new ObjectName("java.lang","type","OperatingSystem"), "TotalPhysicalMemorySize");
        long myPhysicalMemoryInMb = (Long)attribute / MB;
        if (myPhysicalMemoryInMb > 0) {
          return myPhysicalMemoryInMb;
        }
      } catch (Exception e1) {
        LOG.warn("Failed to get total memory size: " + e1.toString());
      }
    }
    return null;
  }

  private Long getProcessorsCount() {
    final OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    long myAvailableProcessors = operatingSystemMXBean.getAvailableProcessors();
    if (myAvailableProcessors > 0){
      return myAvailableProcessors;
    }
    LOG.warn("Failed to get CPU count.");
    return null;
  }

  private void publishStaticParameters() {
    final Long physicalMemorySizeMB = getPhysicalMemorySizeMB();
    if (physicalMemorySizeMB != null) {
      myConfig.addConfigurationParameter(PHYSICAL_MEMORY_KEY, String.valueOf(physicalMemorySizeMB));
    } else {
      LOG.warn("Failed to detect physical memory size. Property " + PHYSICAL_MEMORY_KEY + " will not be set");
    }

    final Long processorsCount = getProcessorsCount();
    if (processorsCount != null) {
      myConfig.addConfigurationParameter(PROCESSORS_COUNT_KEY, String.valueOf(processorsCount));
    } else {
      LOG.warn("Failed to detect number of processors. Property " + PROCESSORS_COUNT_KEY + " will not be set");
    }
  }

  private void publishFreeSpace() {
    final Long space = FileUtil.getFreeSpace(myConfig.getWorkDirectory());
    if (space != null && space >= 0) {
      final long sizeMb = space / MB;
      myConfig.addConfigurationParameter(FREE_SPACE_KEY, String.valueOf(sizeMb));
    } else {
      LOG.debug("Failed to detect number of processors. Property " + FREE_SPACE_KEY + " will not be set");
    }
  }

}
