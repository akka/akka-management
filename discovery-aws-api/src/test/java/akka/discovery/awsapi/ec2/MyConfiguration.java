/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.awsapi.ec2;

//#custom-client-config
// package com.example;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.retry.PredefinedRetryPolicies;

class MyConfiguration extends ClientConfiguration {

  public MyConfiguration() {

    setProxyHost(".."); // and/or other things you would like to set

    setRetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY);
    // If you're using this module for bootstrapping your Akka cluster,
    // Cluster Bootstrap already has its own retry/back-off mechanism. To avoid RequestLimitExceeded errors from AWS,
    // disable retries in the EC2 client configuration.
  }

}
//#custom-client-config
