package io.scalecube.gateway;

import io.scalecube.services.annotations.ServiceMethod;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.rapidoid.io.IO;
import org.rapidoid.setup.On;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class ApiGateway {

  private static ObjectMapper mapper = new ObjectMapper();

  static void configure() {
    mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
        .withFieldVisibility(Visibility.ANY)
        .withGetterVisibility(Visibility.NONE)
        .withSetterVisibility(Visibility.NONE)
        .withCreatorVisibility(Visibility.NONE));
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  public static final class RouteInfo {

    private Builder builder;
    private String action;
    private String url;

    public RouteInfo(Builder builder, String action, String url) {
      this.builder = builder;
      this.action = action;
      this.url = url;
    }

    /**
     * Builder mapping http request to target service method name.
     * 
     * @param methodName to direct the http request to.
     * @return this builder.
     */
    public Builder to(String methodName) {
      On.port(builder.port).route(this.action, this.url).plain(req -> {
        req.async();

        Method method = builder.serviceMethods.get(methodName);
        Class<?> requestType = null;
        if (method.getParameterCount() > 0) {
          requestType = method.getParameterTypes()[0];
        }

        Object request = mapper.readValue(req.body(), requestType);
        Object result = method.invoke(builder.serviceInstance, request);

        if (result != null && result instanceof CompletableFuture) {
          CompletableFuture<?> future = (CompletableFuture<?>) result;
          future.whenComplete((success, error) -> {
            try {
              IO.write(req.response().out(), mapper.writeValueAsBytes(success));
              req.done();
            } catch (JsonProcessingException e) {
              IO.write(req.response().out(), "{\"message\":\"" + e.getMessage() + "\"}");
              req.done();
            }
          });
        }


        return req;

      });

      return this.builder;
    }


  }

  public static final class Builder {

    private ConcurrentMap<String, Method> serviceMethods = new ConcurrentHashMap<>();

    private Object serviceInstance;

    private Object api;

    private int port;

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    /**
     * Builder of the Gateway.
     * 
     * @param serviceApi to route requests to.
     * @return Builder.
     */
    public Builder api(Class serviceApi) {
      this.api = serviceApi;
      getMethodsAnnotatedWith(serviceApi, ServiceMethod.class).stream()
          .forEach(m -> {
            serviceMethods.putIfAbsent(m.getName(), m);
          });
      return this;
    }

    public Builder instance(Object serviceInstance) {
      this.serviceInstance = serviceInstance;
      return this;
    }

    public RouteInfo route(String action, String url) {
      return new RouteInfo(this, action, url);
    }
  }

  public static Builder builder() {
    configure();
    return new Builder();
  }

  public static List<Method> getMethodsAnnotatedWith(final Class<?> type,
      final Class<? extends Annotation> annotation) {
    List<Method> methods = Arrays.asList(type.getDeclaredMethods());
    return methods.stream()
        .filter(m -> m.isAnnotationPresent(annotation))
        .collect(Collectors.toList());
  }
}
