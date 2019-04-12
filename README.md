# luoying web framework
Luoying web framework contains a bundle of components to accelerate J2EE development

## Maven地址
```
<dependency>
  <groupId>com.whlylc</groupId>
  <artifactId>luoying-web</artifactId>
  <version>0.1.6</version>
</dependency>
```

## http concat组件
主流的http1.1算是短连接,合并js,css为单个文件,使用浏览器缓存,gzip传输等手段能有效的减少浏览器
和服务器的交互次数和减小传输数据,在有限的资源下可以提高一下网站响应速度和负载。小开发
团队折腾不起前后端分离,享受不到大前端webpack,gulp带来的福利, 阿里的nginx concat可惜只支持本地文件,
做反向代理时无能为力, MVC后端的写页面不讲究,是有一些开源的组件是可以合并js和css,但不尽人意,多个css
的url语法的相对路径没处理,不支持ETag缓存,gzip, 没考虑热更新等, 所以造了luoying concat这个轮子。

Servlet使用例子
```java
package com.whlylc.web.concat;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Zeal on 2019/4/9 0009.
 */
public class ConcatServlet extends HttpServlet {

    private ResourceConcat resourceConcat = null;

    @Override
    public void init() throws ServletException {
        ServletContext servletContext = this.getServletContext();
        resourceConcat = new ResourceConcat();
        //默认不开启gzip,资源文件超过最小值(这里设置10K)才生效
        resourceConcat.setCompressionMinSize(10 * 1024);
        //资源文件最好能统一个编码,建议为默认的UTF-8
        resourceConcat.setResourceEncoding(StandardCharsets.UTF_8);
        //开启gzip时使用的Transfer-Encoding为chunked,无gzip时Content-Length才设置,这个参数通常不用设置
        //resourceConcat.setResponseBufferSize(10 * 1024);
        try {
            //开启资源文件监控,使用NIO WatchService,可实时更新内存中的缓存的资源文件内容,默认采访过的js,css加载到内存.
            //watchResources()方法不指定特定目录则监控除去WEB-INF外的所有文件夹
            //部署后基本不会变化的不开启省些资源
            resourceConcat.watchResources(this.getServletContext(), "/js", "/css");
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resourceConcat.concat(req, resp);
    }

}
```

Spring MVC使用例子,用ResourceConcatFactoryBean注入,ConcatCtrl中使用
```java
package com.whlylc.web.concat;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.Charset;

/**
 * Resource concat spring factory bean
 * Created by Zeal on 2019/4/10 0010.
 */
public class ResourceConcatFactoryBean implements FactoryBean<ResourceConcat>,ApplicationContextAware,InitializingBean,DisposableBean {

    private ResourceConcat resourceConcat = new ResourceConcat();

    private String[] resourcePaths = null;

    private WebApplicationContext webApplicationContext = null;

    public ResourceConcatFactoryBean setCompressionMinSize(int compressionMinSize) {
        resourceConcat.setCompressionMinSize(compressionMinSize);
        return this;
    }

    public ResourceConcatFactoryBean setResourceEncoding(Charset resourceEncoding) {
        this.resourceConcat.setResourceEncoding(resourceEncoding);
        return this;
    }

    public ResourceConcatFactoryBean setResponseBufferSize(int size) {
        this.resourceConcat.setResponseBufferSize(size);
        return this;
    }

    public ResourceConcatFactoryBean setWatchResourcePaths(String... resourcePaths) {
        this.resourcePaths = resourcePaths;
        return this;
    }

    @Override
    public ResourceConcat getObject() throws Exception {
        return this.resourceConcat;
    }

    @Override
    public Class<?> getObjectType() {
        return ResourceConcat.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof WebApplicationContext) {
            this.webApplicationContext = (WebApplicationContext) applicationContext;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.resourcePaths != null && this.resourcePaths.length > 0) {
            this.resourceConcat.watchResources(this.webApplicationContext.getServletContext(), this.resourcePaths);
        }
    }

    @Override
    public void destroy() throws Exception {
        this.resourceConcat.destroy();
    }
}

```
```java
@Controller
public class ConcatCtrl {

    @Autowired
    private ResourceConcat resourceOptimizer = null;

    @RequestMapping("/jscss")
    @ResponseBody
    public void jscss(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.resourceOptimizer.concat(request, response);
    }
}
```
Enjoy it!
