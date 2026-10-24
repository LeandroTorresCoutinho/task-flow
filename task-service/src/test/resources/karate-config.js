function fn() {
  var config = {
    authBaseUrl: karate.properties['auth.base-url'],
    taskBaseUrl: karate.properties['task.base-url']
  };
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);
  return config;
}
