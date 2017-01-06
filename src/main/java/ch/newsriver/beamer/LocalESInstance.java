package ch.newsriver.beamer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import java.io.IOException;

/**
 * Created by eliapalme on 06.01.17.
 */
public class LocalESInstance {

    private static final Logger logger = LogManager.getLogger(LocalESInstance.class);
    Client client;


    public LocalESInstance(String path) {

        Settings settings = Settings.settingsBuilder()
                .put("node.http.enabled", false)
                .put("index.gateway.type", "none")
                .put("index.store.type", "simplefs")
                .put("index.number_of_shards", 1)
                .put("path.home", path)
                .put("index.number_of_replicas", 0).build();
        Node node = NodeBuilder.nodeBuilder().local(true).settings(settings).node();
        this.client = node.client();

    }

    public boolean replicateIndex(String name, Client remote) {

        CreateIndexRequestBuilder indexBuilder = this.client.admin().indices().prepareCreate("newsriver");
        initializeSettings(remote, name, indexBuilder);
        initializeMappings(remote, name, indexBuilder);

        try {
            indexBuilder.execute().actionGet();
            return true;
        } catch (Exception e) {
            return false;
        }

    }

    public void setLocalTTL(String name, String ttl) {

        //Updating local newsriver TTL
        this.client.admin().indices().preparePutMapping(name)
                .setType("article")
                .setSource("{\n" +
                        "    \"_ttl\": {\n" +
                        "      \"enabled\": true,\n" +
                        "      \"default\": \"" + ttl + "\"\n" +
                        "    }}")
                .execute()
                .actionGet();
    }


    public Client getClient() {
        return this.client;
    }


    private void initializeMappings(Client client, String copyFrom, CreateIndexRequestBuilder indexBuilder) {


        GetMappingsResponse getMappingsResponse =
                client.admin().indices().prepareGetMappings(copyFrom).execute().actionGet();
        ImmutableOpenMap<String, MappingMetaData> mappingsForIndex = getMappingsResponse.getMappings().get(copyFrom);
        mappingsForIndex.forEach(item -> {
            try {
                indexBuilder.addMapping(item.key, item.value.sourceAsMap());
            } catch (IOException e) {
                logger.warn("Could not add the mapping for {} from the index {}", item.key, copyFrom, e);
            }
        });

    }

    private void initializeSettings(Client client, String copyFrom, CreateIndexRequestBuilder indexBuilder) {


        GetSettingsResponse getSettingsResponse =
                client.admin().indices().prepareGetSettings(copyFrom).execute().actionGet();
        ImmutableOpenMap<String, Settings> indexToSettings = getSettingsResponse.getIndexToSettings();
        indexBuilder.setSettings(indexToSettings.get(copyFrom));


    }
}
