// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.commissioner.tasks.subtasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yb.client.YBClient;

import com.yugabyte.yw.commissioner.AbstractTaskBase;
import com.yugabyte.yw.common.services.YBClientService;
import com.yugabyte.yw.forms.ITaskParams;
import com.yugabyte.yw.forms.UniverseTaskParams;
import com.yugabyte.yw.models.Universe;

import play.api.Play;

public class WaitForLeadersOnPreferredOnly extends AbstractTaskBase {
  public static final Logger LOG = LoggerFactory.getLogger(WaitForLeadersOnPreferredOnly.class);

  // The YB client to use.
  private YBClientService ybService = null;

  // Timeout for failing to complete load balance. Currently we do no timeout.
  // NOTE: This is similar to WaitForDataMove for blacklist removal.
  private static final long TIMEOUT_SERVER_WAIT_MS = Long.MAX_VALUE;

  // Parameters for data move wait task.
  public static class Params extends UniverseTaskParams {}

  @Override
  protected Params taskParams() {
    return (Params)taskParams;
  }

  @Override
  public void initialize(ITaskParams params) {
    super.initialize(params);
    ybService = Play.current().injector().instanceOf(YBClientService.class);
  }

  @Override
  public String getName() {
    return super.getName() + "(" + taskParams().universeUUID + ")";
  }

  @Override
  public void run() {
    Universe universe = Universe.get(taskParams().universeUUID);
    String hostPorts = universe.getMasterAddresses();
    boolean ret = false;
    YBClient client = null;
    try {
      LOG.info("Running {}: hostPorts={}.", getName(), hostPorts);
      client = ybService.getClient(hostPorts);
      ret = client.waitForAreLeadersOnPreferredOnlyCondition(TIMEOUT_SERVER_WAIT_MS);
    } catch (Exception e) {
      LOG.error("{} hit error : {}", getName(), e.getMessage());
      throw new RuntimeException(e);
    } finally {
      ybService.closeClient(client, hostPorts);
    }
    if (!ret) {
      throw new RuntimeException(getName() + " did not complete.");
    }
  }
}