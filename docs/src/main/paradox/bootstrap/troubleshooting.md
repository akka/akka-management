# Troubleshooting

To diagnose bootstrap formation issues set the bootstrap logger to `INFO`, for example if using logback:

```xml
 <!-- Set bootstrap formation logs to INFO -->
<logger name="akka.management.cluster.bootstrap.ClusterBootstrap" level="INFO"/>
```

If that doesn't make it clear what is going on then try `DEBUG'.


## Kubernetes API `HTTP chunk size exceeds the configured limit` during contact point discovery 

Increase the max chunk size with:

```
akka.http.client.parsing.max-chunk-size = 20m
```

This should only be necessary if the cluster size is 100s of nodes.
