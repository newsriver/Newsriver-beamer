package ch.newsriver.beamer;

import ch.newsriver.data.content.Article;
import ch.newsriver.data.content.ArticleFactory;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Created by eliapalme on 05.10.17.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LocalESInstanceTest {

    static Path esFolder;
    static LocalESInstance localES;
    static String indexName = "newsriver";

    @BeforeClass
    public static void initialize() throws Exception {
        esFolder = Files.createTempDirectory("es-home");
        localES = new LocalESInstance(esFolder.toAbsolutePath().toString());
        indexName += "es-tmp-" + System.currentTimeMillis() / 1000;
    }

    @AfterClass
    public static void shutdown() throws Exception {
        FileUtils.deleteDirectory(new File(esFolder.toAbsolutePath().toUri()));
    }

    @Test
    public void test_A_addArticle() throws Exception {
        String urlHash = "BKnFrpLSWFjtvpGconOPXk8Mzl35WpywMJQV_iqI4SrW817UYkga7HbwpdY9V9pFtFG-OZwn84p4DFQaAhqzvg";
        Article article = new Article();
        article.setId(urlHash);
        article.setUrl("https://www.welt.de/politik/ausland/article169331166/Ueber-ein-Thema-will-Trump-in-Las-Vegas-nicht-sprechen.html");
        ArticleFactory.getInstance().saveArticle(article, urlHash, indexName, localES.getClient());
    }

    @Test
    public void test_B_searchArticle() throws Exception {
        String id = "BKnFrpLSWFjtvpGconOPXk8Mzl35WpywMJQV_iqI4SrW817UYkga7HbwpdY9V9pFtFG-OZwn84p4DFQaAhqzvg";
        Article article = ArticleFactory.getInstance().getArticle(id, localES.getClient());
        if (article == null) {
            throw new Exception("Article not found!");
        }
    }

    @Test
    public void test_C_deleteIndex() throws Exception {
        IndicesAdminClient adminClient = localES.getClient().admin().indices();
        DeleteIndexResponse delete = adminClient.delete(new DeleteIndexRequest(indexName)).actionGet();
        if (!delete.isAcknowledged()) {
            throw new Exception("Unable to delete index");
        }
    }

    /*

    final ClusterStateRequest clusterStateRequest = new ClusterStateRequest();
    final IndicesOptions strictExpandIndicesOptions = IndicesOptions.strictExpand();
    final GetIndexRequest getIndexRequest = new GetIndexRequest().indices("newsriver*").features(GetIndexRequest.Feature.ALIASES);
    final HttpClient client = new DefaultHttpClient();
    final HttpGet request = new HttpGet("http://elasticsearch-5.6.marathon.services.newsriver.io:9200/_cat/indices/newsriver*?h=index");



    @Test
    public void test_Performaces() throws Exception {

        //Client client = localES.getClient();
        Client client = ElasticsearchUtil.getInstance().getClient();

        clusterStateRequest.clear().metaData(true);
        clusterStateRequest.indicesOptions(strictExpandIndicesOptions);


        long startA = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            getIndicesA(client);
        }
        long endA = System.currentTimeMillis();
        long startB = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            getIndicesB(client);
        }
        long endB = System.currentTimeMillis();
        long startC = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            getIndicesC();
        }
        long endC = System.currentTimeMillis();
        System.out.print("time A:" + (endA - startA) + " time B:" + (endB - startB) + " time C:" + (endC - startC));
    }


    public String[] getIndicesA(Client client) throws Exception {

        return client.admin().cluster().state(clusterStateRequest).get().getState().getMetaData().getConcreteAllOpenIndices();
    }

    public String[] getIndicesB(Client client) throws Exception {

        return client.admin().indices().getIndex(getIndexRequest).actionGet().getIndices();
    }

    public String getIndicesC() throws Exception {

        HttpResponse response = client.execute(request);
        return EntityUtils.toString(response.getEntity());
    }*/


}
