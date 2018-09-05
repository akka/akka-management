# Marathon definitions for testing API responses

##### Directory structure

`.`/`specs`/`marathon_<version>`/`<container runtime>`/`<expected clusters>`/`<service type>` 

###### Examples:

`.`/`specs`/`marathon_1.6`/`mesos`/`multi`/`pods` - pod specs that define multiple clusters/actor systems running on the Mesos container runtime for Marathon 1.6. 

`.`/`specs`/`marathon_1.6`/`docker`/`single`/`apps` - app specs that define a single cluster/actor system running on the Docker container runtime for Marathon 1.6. 

### Vagrant

The specs were created for and tested on DC/OS using [dcos-vagrant](https://github.com/dcos/dcos-vagrant).

The config used can be found under `.`/`specs`/`marathon_<version>`/`vagrant`

### Scripts

###### deploy.pl
Deploys multiple Marathon specs as services to DC/OS

###### remove.pl
Removing DC/OS services.

> Use with care! The service names are hardcoded and will be removed with `--force`
