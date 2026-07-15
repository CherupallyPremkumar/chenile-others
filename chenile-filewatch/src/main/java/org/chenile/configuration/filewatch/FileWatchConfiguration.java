package org.chenile.configuration.filewatch;


import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chenile.filewatch.handler.FileProcessor;
import org.chenile.filewatch.handler.FileWatchEventLogger;
import org.chenile.filewatch.handler.FileWatcherExecutorService;
import org.chenile.filewatch.init.ChenileFileWatchInitializer;
import org.chenile.filewatch.init.FileWatchBuilder;
import org.chenile.filewatch.init.FileWatchSubscribersInitializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:${chenile.properties:chenile.properties}")
@EnableConfigurationProperties(FileWatchProperties.class)
public class FileWatchConfiguration {
	
    @Bean
    public ChenileFileWatchInitializer fileWatchInitializer(FileWatchProperties properties){
        return new ChenileFileWatchInitializer(properties);
    }
    
    @Bean 
    public FileWatchSubscribersInitializer fileWatchSubscribersInitializer() {
    	return new FileWatchSubscribersInitializer();
    }
    
    @Profile("!unittest")
    @Bean FileSystem fileWatcherFileSystem(){
    	return FileSystems.getDefault();
    }
    @Bean FileWatcherExecutorService fileWatcherExecutorService(@Qualifier("fileWatcherFileSystem") FileSystem fileSystem,
    		FileWatchProperties properties) {
    	return new FileWatcherExecutorService(properties,fileSystem);
    }
    
    @Bean ExecutorService executorService(FileWatchProperties properties) {
    	return Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentFiles()));
    }
    
    @Bean
    public FileWatchBuilder fileWatchBuilder() {
    	return  new FileWatchBuilder();
    }
    
    @Bean FileProcessor fileProcessor() {
    	return new FileProcessor();
    }
    
    @Bean FileWatchEventLogger fileWatchEventLogger() {
    	return new FileWatchEventLogger();
    }
    
}
