/*
 * Copyright 2014 Metamarkets Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.guice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 *
 */
public class JsonConfigTester<T>
{

  private static final String configPrefix = "druid.test.prefix";
  private Injector injector;
  private final Class<T> clazz = (Class<T>)((ParameterizedType) new TypeReference<T>(){}.getType()).getActualTypeArguments()[0];

  private Map<String, String> propertyValues = new HashMap<>();
  private int assertions = 0;
  private Properties testProperties = new Properties();

  private static String getPropertyKey(Field field){
    JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
    if (null != jsonProperty) {
      return (jsonProperty.value() == null || jsonProperty.value().isEmpty()) ? field.getName() : jsonProperty.value();
    }
    return null;
  }

  private final Module simpleJsonConfigModule = new Module()
  {
    @Override
    public void configure(Binder binder)
    {
      binder.bindConstant().annotatedWith(Names.named("serviceName")).to("druid/test");
      binder.bindConstant().annotatedWith(Names.named("servicePort")).to(0);
      JsonConfigProvider.bind(binder, configPrefix, clazz);
    }
  };


  protected final void validateEntries(T config)
      throws IllegalAccessException, NoSuchMethodException, InvocationTargetException
  {
    for (Field field : clazz.getDeclaredFields()) {
      final String propertyKey = getPropertyKey(field);
      if (null != propertyKey) {
        field.setAccessible(true);
        Assert.assertEquals(propertyValues.get(propertyKey), field.get(config));
        ++assertions;
      }
    }
  }
  protected JsonConfigurator configurator;
  @Before
  public void setup()
  {
    assertions = 0;

    propertyValues.clear();
    testProperties.clear();
    for (Field field : clazz.getDeclaredFields()) {
      final String propertyKey = getPropertyKey(field);
      if (null != propertyKey) {
        propertyValues.put(propertyKey, UUID.randomUUID().toString());
      }
    }
    testProperties.putAll(System.getProperties());
    testProperties.putAll(propertyValues);
    injector = Guice.createInjector(
        ImmutableList.<Module>of(simpleJsonConfigModule)
    );
    configurator = injector.getBinding(JsonConfigurator.class).getProvider().get();
  }
  @Test
  public final void simpleInjectionTest()
      throws IllegalAccessException, NoSuchMethodException, InvocationTargetException
  {

    JsonConfigurator configurator = injector.getBinding(JsonConfigurator.class).getProvider().get();

    JsonConfigProvider<T> configProvider = JsonConfigProvider.of(configPrefix, clazz);
    configProvider.inject(testProperties, configurator);

    validateEntries(configProvider.get().get());
    Assert.assertEquals(testProperties.size(), assertions);
  }
}
