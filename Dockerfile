FROM openjdk:11
COPY . /bumpdeps
RUN /bumpdeps/gradlew --no-daemon -p /bumpdeps installDist
ENTRYPOINT ["/bumpdeps/build/install/bumpdeps/bin/bumpdeps"]
