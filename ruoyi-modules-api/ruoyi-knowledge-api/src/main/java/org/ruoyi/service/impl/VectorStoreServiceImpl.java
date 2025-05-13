package org.ruoyi.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.service.ConfigService;
import org.ruoyi.domain.bo.QueryVectorBo;
import org.ruoyi.domain.bo.StoreEmbeddingBo;
import org.ruoyi.service.VectorStoreService;
import org.springframework.stereotype.Service;

import static dev.langchain4j.model.openai.OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量库管理
 * @author ageer
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final ConfigService configService;

    Map<String,EmbeddingStore<TextSegment>> storeMap = new HashMap<>();

    @Override
    public void createSchema(String kid,String modelName) {
        EmbeddingStore<TextSegment> embeddingStore;
        switch (modelName) {
            case "weaviate" -> {
                String protocol = configService.getConfigValue("weaviate", "protocol");
                String host = configService.getConfigValue("weaviate", "host");
                String className = configService.getConfigValue("weaviate", "classname");
                embeddingStore = WeaviateEmbeddingStore.builder()
                        .scheme(protocol)
                        .host(host)
                        .objectClass(className + kid)
                        .scheme(protocol)
                        .avoidDups(true)
                        .consistencyLevel("ALL")
                        .build();
            }
            case "milvus" -> {
                String uri = configService.getConfigValue("milvus", "host");
                String collection = configService.getConfigValue("milvus", "collection");
                String dimension = configService.getConfigValue("milvus", "dimension");
                embeddingStore = MilvusEmbeddingStore.builder()
                        .uri(uri)
                        .collectionName(collection + kid)
                        .dimension(Integer.parseInt(dimension))
                        .build();
            }
            case "qdrant" -> {
                String host = configService.getConfigValue("qdrant", "host");
                String port = configService.getConfigValue("qdrant", "port");
                String collectionName = configService.getConfigValue("qdrant", "collectionName");
                embeddingStore = QdrantEmbeddingStore.builder()
                        .host(host)
                        .port(Integer.parseInt(port))
                        .collectionName(collectionName)
                        .build();
            }
            default -> {
                //使用内存
                embeddingStore = new InMemoryEmbeddingStore<>();
            }
        }
        storeMap.put(kid,embeddingStore);
    }

    @Override
    public void storeEmbeddings(StoreEmbeddingBo storeEmbeddingBo) {
        EmbeddingStore<TextSegment> store = storeMap.get(storeEmbeddingBo.getKid());
        EmbeddingModel embeddingModel = getEmbeddingModel(storeEmbeddingBo.getModelName(),
                storeEmbeddingBo.getApiKey(), storeEmbeddingBo.getBaseUrl());
        for (int i = 0; i < storeEmbeddingBo.getChunkList().size(); i++) {
            Map<String, Object> dataSchema = new HashMap<>();
            dataSchema.put("kid", storeEmbeddingBo.getKid());
            dataSchema.put("docId", storeEmbeddingBo.getKid());
            dataSchema.put("fid", storeEmbeddingBo.getFids().get(i));
            Response<Embedding> response = embeddingModel.embed(storeEmbeddingBo.getChunkList().get(i));
            Embedding embedding = response.content();
            TextSegment segment = TextSegment.from(storeEmbeddingBo.getChunkList().get(i));
            segment.metadata().putAll(dataSchema);

            store.add(embedding,segment);
        }
    }

    @Override
    public List<String> getQueryVector(QueryVectorBo queryVectorBo) {
        EmbeddingStore<TextSegment> store = storeMap.get(queryVectorBo.getKid());

        EmbeddingModel embeddingModel = getEmbeddingModel(queryVectorBo.getModelName(),
                queryVectorBo.getApiKey(), queryVectorBo.getBaseUrl());
        Filter simpleFilter = new IsEqualTo("kid", queryVectorBo.getKid());
        Embedding queryEmbedding = embeddingModel.embed(queryVectorBo.getQuery()).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(queryVectorBo.getMaxResults())
                // 添加过滤条件
                .filter(simpleFilter)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(embeddingSearchRequest).matches();

        List<String> results = new ArrayList<>();

        matches.forEach(embeddingMatch -> results.add(embeddingMatch.embedded().text()));
        return results;
    }


    @Override
    public void removeByKid(String kid) {
        EmbeddingStore<TextSegment> store = storeMap.get(kid);

        // 根据条件删除向量数据
        Filter simpleFilter = new IsEqualTo("kid", kid);
        store.removeAll(simpleFilter);
    }

    @Override
    public void removeByDocId(String kid, String docId) {
        EmbeddingStore<TextSegment> store = storeMap.get(kid);
        // 根据条件删除向量数据
        Filter simpleFilterByDocId = new IsEqualTo("docId", docId);
        store.removeAll(simpleFilterByDocId);
    }

    @Override
    public void removeByKidAndFid(String kid, String fid) {
        EmbeddingStore<TextSegment> store = storeMap.get(kid);
        // 根据条件删除向量数据
        Filter simpleFilterByKid = new IsEqualTo("kid", kid);
        Filter simpleFilterFid = new IsEqualTo("fid", fid);
        Filter simpleFilterByAnd = Filter.and(simpleFilterFid, simpleFilterByKid);
        store.removeAll(simpleFilterByAnd);
    }

    /**
     * 获取向量模型
     */
    public EmbeddingModel getEmbeddingModel(String modelName,String apiKey,String baseUrl) {
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder().build();
        if(TEXT_EMBEDDING_3_SMALL.toString().equals(modelName)) {
             embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(TEXT_EMBEDDING_3_SMALL)
                    .build();
        // TODO 添加枚举
        }else if("quentinz/bge-large-zh-v1.5".equals(modelName)) {
            embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
        }
        return embeddingModel;
    }

}
