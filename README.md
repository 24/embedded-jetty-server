# embedded-jetty-server

This is a maven project.

使用maven构建:

```
$ mvn clean compile assembly:single
```

使用构建好的jar包启动:

```
$ java -jar /path/to/embedded-jetty-server-package.jar --webapp=/path/to/project
```

参数:

`-p, --port` 指定一个端口号, 默认: `8080`  
`-w, --webapp` 指定项目路径, 该参数必传  
`-L, --logs` 指定访问日志的输出目录, 默认为webapp下的logs目录  
`-H, --host` 指定主机名, 默认: `127.0.0.1`
