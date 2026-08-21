package io.havenbot.server.config;

import io.havenbot.server.auth.OperatorAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final OperatorAuthInterceptor operatorAuthInterceptor;

    public WebMvcConfig(OperatorAuthInterceptor operatorAuthInterceptor) {
        this.operatorAuthInterceptor = operatorAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operatorAuthInterceptor);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // The MJPEG endpoint is an intentionally long-lived StreamingResponseBody.
        // A zero Servlet async timeout keeps Spring from terminating it every 30s;
        // client disconnects still surface as an IOException during frame writes.
        configurer.setDefaultTimeout(0);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/{spring:[^.]*}").setViewName("forward:/index.html");
        registry.addViewController("/bots/{spring:[^.]*}").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootDist = toResourceLocation(Paths.get("web/dist"));
        String siblingDist = toResourceLocation(Paths.get("../web/dist"));

        registry.addResourceHandler("/index.html", "/favicon.ico")
                .addResourceLocations(rootDist, siblingDist);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(rootDist + "assets/", siblingDist + "assets/");
        registry.addResourceHandler("/game-icons/**")
                .addResourceLocations(rootDist + "game-icons/", siblingDist + "game-icons/");
    }

    private String toResourceLocation(Path path) {
        String location = path.toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
