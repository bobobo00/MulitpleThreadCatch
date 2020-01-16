import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomText;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.NlpAnalysis;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ClassName MulitpleThread
 * @Description TODO
 * @Auther danni
 * @Date 2020/1/9 18:55]
 * @Version 1.0
 **/

public class MulitpleThread {

    static  class Job implements Runnable{
        private static DataSource dataSource;
        private static String sql;
        private static WebClient webClient;
        private static MessageDigest messageDigest;
        private static String url;

        public Job(DataSource dataSource, String sql,String url) {
            try {
                this.dataSource=dataSource;
                this.sql = sql;
                this.url=url;
                webClient=new WebClient(BrowserVersion.CHROME);
                webClient.getOptions().setJavaScriptEnabled(false);
                webClient.getOptions().setCssEnabled(false);
                messageDigest=MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

        }
        @Override
        public void run() {
            try {
                Connection connection=dataSource.getConnection();
                PreparedStatement statement=connection.prepareStatement(sql);
                HtmlPage page=webClient.getPage(url);

                String xpath;
                DomText domText;

                xpath = "//div[@class='cont']/h1/text()";
                domText = (DomText)page.getBody().getByXPath(xpath).get(0);
                String title=domText.asText();//题目

                xpath = "//div[@class='cont']/p[@class='source']/a[1]/text()";
                domText = (DomText) page.getBody().getByXPath(xpath).get(0);
                String dynasty=domText.asText();//朝代

                xpath = "//div[@class='cont']/p[@class='source']/a[2]/text()";
                domText = (DomText)page.getBody().getByXPath(xpath).get(0);
                String auther=domText.asText();//作者

                xpath = "//div[@class='cont']/div[@class='contson']";
                HtmlElement element  = (HtmlElement)page.getBody().getByXPath(xpath).get(0);
                String content=element.getTextContent().trim();//正文
                //计算SHA-256；
                String s=title+content;
                messageDigest.update(s.getBytes("UTF-8"));
                byte[] result=messageDigest.digest();
                StringBuilder sha256=new StringBuilder();
                for(byte b: result){
                    sha256.append(String.format("%02x",b)); }//计算分词
                List<Term> termList=new ArrayList<>();
                termList.addAll(NlpAnalysis.parse(title).getTerms());
                termList.addAll(NlpAnalysis.parse(content).getTerms());
                //保存到数据库
                List<String> words=new ArrayList<>();
                for(Term term:termList){
                    if(term.getNatureStr().equals("w")){
                        continue; }if(term.getNatureStr().equals("null")){
                        continue; }if(term.getRealName().length()<2){
                        continue; }words.add(term.getRealName());
                }
                String insertWords=String.join(",",words);
                statement.setString(1,sha256.toString());
                statement.setString(2,dynasty);
                statement.setString(3,title);
                statement.setString(4,auther);
                statement.setString(5,content);
                statement.setString(6,insertWords);
                statement.executeUpdate();
                System.out.println(title+"插入成功！");
            } catch (SQLException e) {
                if(!e.getMessage().contains("Duplicate entry")){
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }

    public static void main(String[] args) {
        DataSource dataSource2=new MysqlConnectionPoolDataSource();
        ((MysqlConnectionPoolDataSource) dataSource2).setServerName("127.0.0.1");
        ((MysqlConnectionPoolDataSource) dataSource2).setPort(3306);
        ((MysqlConnectionPoolDataSource) dataSource2).setUser("root");
        ((MysqlConnectionPoolDataSource) dataSource2).setPassword("0813");
        ((MysqlConnectionPoolDataSource) dataSource2).setDatabaseName("java11");
        ((MysqlConnectionPoolDataSource) dataSource2).setUseSSL(false);
        ((MysqlConnectionPoolDataSource) dataSource2).setCharacterEncoding("UTF8");
        String sql="Insert into tangpoetry"+
                "(sha256,dynasty,title,author,content,words)"+
                "values(?,?,?,?,?,?)";

        WebClient webClient=new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setCssEnabled(false);


        ExecutorService executorService=Executors.newFixedThreadPool(30);
            //下载详情页
        List<String> detailUrlList=new ArrayList<>();
        try {
            HtmlPage page = webClient.getPage("https://www.gushiwen.org/gushi/tangshi.aspx");
            List<HtmlElement> divs = page.getBody().getElementsByAttribute("div", "class", "typecont");
            for (HtmlElement div : divs) {
                List<HtmlElement> as = div.getElementsByTagName("a");
                for (HtmlElement a : as) {
                    String detailUrl = a.getAttribute("href");
                    detailUrlList.add(detailUrl);
                    //System.out.println(detailUrl);
                  executorService.execute(new Job(dataSource2,sql,detailUrl));
                }
            }
        }catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
