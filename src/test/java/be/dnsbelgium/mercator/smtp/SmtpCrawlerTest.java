package be.dnsbelgium.mercator.smtp;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.smtp.domain.crawler.*;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpConversation;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.smtp.persistence.repositories.SmtpRepository;
import groovy.util.logging.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static be.dnsbelgium.mercator.smtp.SmtpTestUtils.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j(value = "logger")
class SmtpCrawlerTest {

  private final DuckDataSource dataSource = DuckDataSource.memory();
  private final JdbcClient jdbcClient = JdbcClient.create(dataSource);
  private final SmtpRepository smtpRepository = new SmtpRepository(dataSource);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  SmtpConversationCache conversationCache = new SmtpConversationCache(meterRegistry);

  private final SmtpCrawler smtpCrawler = new SmtpCrawler(smtpRepository, null, conversationCache);

  @Test
  public void createTables() {
    smtpCrawler.createTables();
    List<String> tableNames = jdbcClient.sql("show tables").query(String.class).list();
    System.out.println("tableNames = " + tableNames);
    assertThat(tableNames).contains("smtp_conversation", "smtp_host", "smtp_visit");
  }

  @Test
  public void find() {
    smtpCrawler.createTables();
    SmtpVisit visit = visit();
    smtpRepository.saveVisit(visit);
    List<SmtpVisit> found = smtpCrawler.find(visit.getVisitId());
    assertThat(found).hasSize(1);
    System.out.println("found = " + found);
  }

  @Test
  public void save() {
    smtpCrawler.createTables();
    SmtpVisit visit1 = visit();
    SmtpVisit visit2 = visit();
    smtpCrawler.save(List.of(visit1, visit2));
    List<SmtpVisit> found = smtpCrawler.find(visit1.getVisitId());
    assertThat(found).hasSize(1);
    System.out.println("found = " + found);
  }

  @Test
  public void integrationTest() throws UnknownHostException {
    MxFinder mxFinder = mock(MxFinder.class);
    SmtpIpAnalyzer ipAnalyzer  = mock(SmtpIpAnalyzer.class);
    String IP = "72.20.30.40";
    InetAddress ipAddress = InetAddress.getByName(IP);
    when(mxFinder.findMxRecordsFor("example.org")).thenReturn(MxLookupResult.noMxRecordsFound());
    when(mxFinder.findIpAddresses("example.org")).thenReturn(List.of(ipAddress));
    SmtpConversation smtpConversation = SmtpConversation.builder()
            .ip(IP)
            .banner("HELLO SMTP")
            .connectionTimeMs(123)
            .connectOK(true)
            .startTlsReplyCode(220)
            .build();
    when(ipAnalyzer.crawl(ipAddress)).thenReturn(smtpConversation);
    SmtpAnalyzer smtpAnalyzer = new SmtpAnalyzer(meterRegistry, ipAnalyzer, mxFinder, conversationCache, false, false, 4);
    SmtpCrawler smtpCrawler = new SmtpCrawler(smtpRepository, smtpAnalyzer, new SmtpConversationCache(meterRegistry));

    smtpCrawler.createTables();
    List<SmtpVisit> smtpVisits = smtpCrawler.collectData(new VisitRequest("id-123", "example.org"));
    smtpCrawler.save(smtpVisits);
    List<SmtpVisit> found = smtpCrawler.find("id-123");
    assertThat(found).hasSize(1);
    found.forEach(System.out::println);
    jdbcClient.sql("from smtp_conversation").query().listOfRows().forEach(System.out::println);
    jdbcClient.sql("from smtp_host").query().listOfRows().forEach(System.out::println);
    jdbcClient.sql("from smtp_visit").query().listOfRows().forEach(System.out::println);
  }

}