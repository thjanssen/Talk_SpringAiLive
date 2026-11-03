package com.thorben.janssen.piratestore.support.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
public class IngestionPipeline {

    Logger logger = LoggerFactory.getLogger(IngestionPipeline.class);

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    public List<Resource> getInputFiles() throws IOException {
        return Arrays.asList(
                resourcePatternResolver.getResources("classpath:/docs/*")
        );
    }

    private File getVectorStoreFile() throws IOException {
        Path path = Paths.get("rag");
        String absolutePath = path.toFile().getAbsolutePath() + "/vectorstore.json";
        return new File(absolutePath);
    }
}
