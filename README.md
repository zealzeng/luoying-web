# luoying web framework
Luoying web framework contains a bundle of components to improve J2EE development

## Maven地址
```
<dependency>
  <groupId>com.whlylc</groupId>
  <artifactId>luoying-web</artifactId>
  <version>0.1.1</version>
</dependency>
```

## http concat组件
主流的http1.1算是短连接,合并js,css为单个文件,使用浏览器缓存,gzip传输等手段能有效的减少浏览器
和服务器的交互次数和减小传输数据,在有限的资源下可以提高一下网站响应速度和负载。没一定规模的开发
团队折腾不起前后端分离,享受不到大前端webpack,gulp带来的福利, 阿里的nginx concat可惜只支持本地文件,
做反向代理时无能为力, MVC后端的写页面不讲究,是有一些开源的组件是可以合并js和css,但不尽人意,多个css
的url语法的相对路径没处理,不支持ETag缓存,gzip, 线上修改不支持更新等等, 所以造了luoying concat这个轮子。


 
 
