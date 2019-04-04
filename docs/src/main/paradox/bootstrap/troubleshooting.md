# Troubleshooting

## Kubernetes API `HTTP chunk size exceeds the configured limit` during contact point discovery 

Increase the max chunk size with:

```
akka.http.client.parsing.max-chunk-size = 20m
```

This should only be necessary if the cluster size is 100s of nodes.
