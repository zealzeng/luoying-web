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
