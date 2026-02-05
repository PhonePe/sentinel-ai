/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.embedding;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * A model that uses huggingface models to get embeddings. Model will be downloaded automatically.
 * Check <a href="https://docs.djl.ai/master/docs/load_model.html">...</a> for more information on how to load models
 */
@Slf4j
public class HuggingfaceEmbeddingModel implements EmbeddingModel, AutoCloseable {
    private static final int MAX_LENGTH = 10_000;

    @RequiredArgsConstructor
    private static final class PredictorFactory extends BasePooledObjectFactory<Predictor<String, float[]>> {

        private final ZooModel<String, float[]> zooModel;

        @Override
        public Predictor<String, float[]> create() {
            log.debug("Creating new predictor");
            return zooModel.newPredictor();
        }

        @Override
        public void destroyObject(PooledObject<Predictor<String, float[]>> predictor,
                                  DestroyMode destroyMode) {
            log.info("Closing predictor");
            predictor.getObject().close();
        }

        @Override
        public PooledObject<Predictor<String, float[]>> wrap(Predictor<String, float[]> predictor) {
            return new DefaultPooledObject<>(predictor);
        }
    }

    private final String modelUrl;
    private final int maxLength;
    private final ZooModel<String, float[]> zooModel;

    // The pool is needed as predictor is not threadsafe
    private final GenericObjectPool<Predictor<String, float[]>> predictors;

    public HuggingfaceEmbeddingModel() {
        this(null, MAX_LENGTH);
    }

    @Builder
    @SneakyThrows
    public HuggingfaceEmbeddingModel(String modelUrl, int maxLength) {
        this.modelUrl = Objects.requireNonNullElse(modelUrl,
                                                   "djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2");

        this.maxLength = Math.max(maxLength, MAX_LENGTH);
        System.setProperty("OPT_OUT_TRACKING", "true"); //DJL DIALS HOME ...

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(this.modelUrl)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .optProgress(new ProgressBar())
                .build();

        this.zooModel = criteria.loadModel();
        this.predictors = new GenericObjectPool<>(new PredictorFactory(zooModel));
    }

    @Override
    public void close() {
        predictors.close();
        zooModel.close();
    }

    @Override
    @SneakyThrows
    public float[] getEmbedding(String input) {
        final var predictor = predictors.borrowObject();
        try {
            return predictor.predict(input);
        }
        finally {
            predictors.returnObject(predictor);
        }
    }
}
