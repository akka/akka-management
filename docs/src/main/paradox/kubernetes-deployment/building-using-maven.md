# Building using Maven

There are a number of Maven plugins available for building and deploying Docker images. We're going to use [Fabric8](https://maven.fabric8.io/).
### Git hash based version numbers
    
There are a number of Maven plugins available for interacting with git, we recommend the 
[Maven git commit id plugin](https://github.com/git-commit-id/maven-git-commit-id-plugin). This plugin will allow us to make certain git 
based properties available to the build, which we can then use to compute the version number.

To use it, add this plugin to the build section of your parent POM:

```xml
<plugin>
    <groupId>pl.project13.maven</groupId>
    <artifactId>git-commit-id-plugin</artifactId>
    <version>2.2.6</version>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>revision</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <dateFormat>yyyyMMdd-HHmmss</dateFormat>
        <dotGitDirectory>${project.basedir}/.git</dotGitDirectory>
        <generateGitPropertiesFile>false</generateGitPropertiesFile>
    </configuration>
</plugin>
```

Now, in the properties section of the parent POM, create a version number based on the commit time and id. Using the commit time 
is useful because it makes it possible to sort tags chronologically:

```xml
<properties>
   <version.number>${git.commit.time}.${git.commit.id.abbrev}</version.number>
</properties>
```

@@@note
The version number will only change when you create a new git commit. If you are not using the `latest` tag in your deployment spec, then make sure when you update your project and want to redeploy, that you commit your changes first so you get a new version number, and to ensure that version number correlates to what is in the git repository at that commit hash.
@@@

## Configuring the Fabric8 Docker plugin

```xml
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.26.1</version>
    <configuration>
        <images>
            <image>
                <name>%a</name>
                <build>
                    <from>adoptopenjdk/openjdk8</from>
                    <tags>
                        <tag>latest</tag>
                        <tag>${version.number}</tag>
                    </tags>
                    <entryPoint>
                       java $JAVA_OPTS -cp '/maven/*' $main.class$
                    </entryPoint> 
                    <assembly>
                        <descriptorRef>artifact-with-dependencies</descriptorRef>
                    </assembly>
                </build>
            </image>
        </images>
    </configuration>
    <executions>
       <execution>
           <id>build-docker-image</id>
           <phase>package</phase>
           <goals>
               <goal>build</goal>
           </goals>
       </execution>
     </executions>
</plugin>
```

There are two things to pay careful attention to here. 

* The base image we're using is `adoptopenjdk/openjdk8`. You can use any Docker image that provides a JRE, this is the one we recommend for open source users of Kubernetes.
If you're a RedHat customer, you will likely prefer to use the RedHat certified OpenJDK base images,
which use a RedHat certified OpenJDK build on RHEL, which is also certified by Lightbend for running our products:

```xml
<from>registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift</from>
```

* `docker:build` is added to the `package` phase do that the Docker image is built when running `mvn package`
* Versions `latest` and `${version.number}` are tagged
* Artifact with dependencies is set for the assembly. Meaning your application and its dependencies are added to `/maven` in the Docker image

