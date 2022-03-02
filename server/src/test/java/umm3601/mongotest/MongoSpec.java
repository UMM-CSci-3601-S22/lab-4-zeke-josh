package umm3601.mongotest;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.javalin.plugin.json.JsonMapperKt.JSON_MAPPER_KEY;
import static java.util.Map.entry;
import io.javalin.core.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpCode;
import io.javalin.http.util.ContextUtil;
import io.javalin.plugin.json.JavalinJackson;
import umm3601.user.UserController;
import umm3601.user.User;

import umm3601.todo.TodoController;
import umm3601.todo.Todo;

/**
 * Some simple "tests" that demonstrate our ability to
 * connect to a Mongo database and run some basic queries
 * against it.
 * <p>
 * Note that none of these are actually tests of any of our
 * code; they are mostly demonstrations of the behavior of
 * the MongoDB Java libraries. Thus if they test anything,
 * they test that code, and perhaps our understanding of it.
 * <p>
 * To test "our" code we'd want the tests to confirm that
 * the behavior of methods in things like the UserController
 * do the "right" thing.
 * <p>
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
public class MongoSpec {

  private static final long MAX_REQUEST_SIZE = new JavalinConfig().maxRequestSize;

  private MockHttpServletRequest mockReq = new MockHttpServletRequest();
  private MockHttpServletResponse mockRes = new MockHttpServletResponse();
  private static JavalinJackson javalinJackson = new JavalinJackson();

  private MongoCollection<Document> userDocuments;
  private MongoCollection<Document> todoDocuments;

  private static UserController userController;
  private static TodoController todoController;
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  @BeforeAll
  public static void setupDB() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
      MongoClientSettings.builder()
      .applyToClusterSettings(builder ->
        builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
      .build());

    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  public static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  public void clearAndPopulateDB() {
    mockReq.resetAll();
    mockRes.resetAll();

    userDocuments = db.getCollection("users");
    userDocuments.drop();
    List<Document> testUsers = new ArrayList<>();
    testUsers.add(
      new Document()
        .append("name", "Chris")
        .append("age", 25)
        .append("company", "UMM")
        .append("email", "chris@this.that"));
    testUsers.add(
      new Document()
        .append("name", "Pat")
        .append("age", 37)
        .append("company", "IBM")
        .append("email", "pat@something.com"));
    testUsers.add(
      new Document()
        .append("name", "Jamie")
        .append("age", 37)
        .append("company", "Frogs, Inc.")
        .append("email", "jamie@frogs.com"));

    userDocuments.insertMany(testUsers);

    userController = new UserController(db);


    todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
      new Document()
        .append("owner", "Chris")
        .append("category", "Homework")
        .append("status", true)
        .append("body", "Random words for testing"));
    testTodos.add(
      new Document()
        .append("owner", "Lucy")
        .append("category", "Software Design")
        .append("status", true)
        .append("body", "Dog parks are for dogs"));
    testTodos.add(
      new Document()
      .append("owner", "Fernando")
      .append("category", "Homework")
      .append("status", false)
      .append("body", "Computers are for humans"));
    testTodos.add(
      new Document()
        .append("owner", "Sam")
        .append("category", "Software Design")
        .append("status", true)
        .append("body", "Sam has an id"));

    todoDocuments.insertMany(testTodos);

    todoController = new TodoController(db);
  }

  private Context mockContext(String path) {
    return mockContext(path, Map.of());
  }

  private Context mockContext(String path, Map<String, String> pathParams) {
    return ContextUtil.init(
        mockReq, mockRes,
        path,
        pathParams,
        HandlerType.INVALID,
        Map.ofEntries(
          entry(JSON_MAPPER_KEY, javalinJackson),
          entry(ContextUtil.maxRequestSizeKey, MAX_REQUEST_SIZE)));
  }

  private List<Document> intoUserList(MongoIterable<Document> documents) {
    List<Document> users = new ArrayList<>();
    documents.into(users);
    return users;
  }

  private List<Document> intoTodoList(MongoIterable<Document> documents) {
    List<Document> todos = new ArrayList<>();
    documents.into(todos);
    return todos;
  }

  private int countUsers(FindIterable<Document> documents) {
    List<Document> users = intoUserList(documents);
    return users.size();
  }

  private int countTodos(FindIterable<Document> documents) {
    List<Document> todos = intoTodoList(documents);
    return todos.size();
  }

  @Test
  public void shouldBeThreeUsers() {
    FindIterable<Document> documents = userDocuments.find();
    int numberOfUsers = countUsers(documents);
    assertEquals(3, numberOfUsers, "Should be 3 total users");
  }

  @Test
  public void shouldBeOneChris() {
    FindIterable<Document> documents = userDocuments.find(eq("name", "Chris"));
    int numberOfUsers = countUsers(documents);
    assertEquals(1, numberOfUsers, "Should be 1 Chris");
  }

  @Test
  public void shouldBeTwoOver25() {
    FindIterable<Document> documents = userDocuments.find(gt("age", 25));
    int numberOfUsers = countUsers(documents);
    assertEquals(2, numberOfUsers, "Should be 2 over 25");
  }

  @Test
  public void over25SortedByName() {
    FindIterable<Document> documents
      = userDocuments.find(gt("age", 25))
      .sort(Sorts.ascending("name"));
    List<Document> docs = intoUserList(documents);
    assertEquals(2, docs.size(), "Should be 2");
    assertEquals("Jamie", docs.get(0).get("name"), "First should be Jamie");
    assertEquals("Pat", docs.get(1).get("name"), "Second should be Pat");
  }

  @Test
  public void over25AndIbmers() {
    FindIterable<Document> documents
      = userDocuments.find(and(gt("age", 25),
      eq("company", "IBM")));
    List<Document> docs = intoUserList(documents);
    assertEquals(1, docs.size(), "Should be 1");
    assertEquals("Pat", docs.get(0).get("name"), "First should be Pat");
  }

  @Test
  public void justNameAndEmail() {
    FindIterable<Document> documents
      = userDocuments.find().projection(fields(include("name", "email")));
    List<Document> docs = intoUserList(documents);
    assertEquals(3, docs.size(), "Should be 3");
    assertEquals("Chris", docs.get(0).get("name"), "First should be Chris");
    assertNotNull(docs.get(0).get("email"), "First should have email");
    assertNull(docs.get(0).get("company"), "First shouldn't have 'company'");
    assertNotNull(docs.get(0).get("_id"), "First should have '_id'");
  }

  @Test
  public void justNameAndEmailNoId() {
    FindIterable<Document> documents
      = userDocuments.find()
      .projection(fields(include("name", "email"), excludeId()));
    List<Document> docs = intoUserList(documents);
    assertEquals(3, docs.size(), "Should be 3");
    assertEquals("Chris", docs.get(0).get("name"), "First should be Chris");
    assertNotNull(docs.get(0).get("email"), "First should have email");
    assertNull(docs.get(0).get("company"), "First shouldn't have 'company'");
    assertNull(docs.get(0).get("_id"), "First should not have '_id'");
  }

  @Test
  public void justNameAndEmailNoIdSortedByCompany() {
    FindIterable<Document> documents
      = userDocuments.find()
      .sort(Sorts.ascending("company"))
      .projection(fields(include("name", "email"), excludeId()));
    List<Document> docs = intoUserList(documents);
    assertEquals(3, docs.size(), "Should be 3");
    assertEquals("Jamie", docs.get(0).get("name"), "First should be Jamie");
    assertNotNull(docs.get(0).get("email"), "First should have email");
    assertNull(docs.get(0).get("company"), "First shouldn't have 'company'");
    assertNull(docs.get(0).get("_id"), "First should not have '_id'");
  }

  @Test
  public void ageCounts() {
    AggregateIterable<Document> documents
      = userDocuments.aggregate(
      Arrays.asList(
        /*
         * Groups data by the "age" field, and then counts
         * the number of documents with each given age.
         * This creates a new "constructed document" that
         * has "age" as it's "_id", and the count as the
         * "ageCount" field.
         */
        Aggregates.group("$age",
          Accumulators.sum("ageCount", 1)),
        Aggregates.sort(Sorts.ascending("_id"))
      )
    );
    List<Document> docs = intoUserList(documents);
    assertEquals(2, docs.size(), "Should be two distinct ages");
    assertEquals(25, docs.get(0).get("_id"));
    assertEquals(1, docs.get(0).get("ageCount"));
    assertEquals(37, docs.get(1).get("_id"));
    assertEquals(2, docs.get(1).get("ageCount"));
  }

  @Test
  public void averageAge() {
    AggregateIterable<Document> documents
      = userDocuments.aggregate(
      Arrays.asList(
        Aggregates.group("$company",
          Accumulators.avg("averageAge", "$age")),
        Aggregates.sort(Sorts.ascending("_id"))
      ));
    List<Document> docs = intoUserList(documents);
    assertEquals(3, docs.size(), "Should be three companies");

    assertEquals("Frogs, Inc.", docs.get(0).get("_id"));
    assertEquals(37.0, docs.get(0).get("averageAge"));
    assertEquals("IBM", docs.get(1).get("_id"));
    assertEquals(37.0, docs.get(1).get("averageAge"));
    assertEquals("UMM", docs.get(2).get("_id"));
    assertEquals(25.0, docs.get(2).get("averageAge"));
  }

  @Test
  public void canGetAllUsers() {

    Context ctx = mockContext("api/users");

    userController.getUsers(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = userDocuments.find();

    int numberOfUsers = countUsers(documents);
    assertEquals(3, numberOfUsers);
    assertEquals(3, javalinJackson.fromJsonString(result, User[].class).length);
    assertEquals(numberOfUsers,
       javalinJackson.fromJsonString(result, User[].class).length);
  }

  @Test
  public void canGetAllTodos() {

    Context ctx = mockContext("api/todos");

    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = todoDocuments.find();

    int numberOfTodos = countTodos(documents);

    assertEquals(4, numberOfTodos);
    assertEquals(4, javalinJackson.fromJsonString(result, Todo[].class).length);
    assertEquals(numberOfTodos,
       javalinJackson.fromJsonString(result, Todo[].class).length);
  }

  @Test
  public void canGetUsersWithSpecifiedAge() {

    mockReq.setQueryString("age=37");
    Context ctx = mockContext("api/users");

    userController.getUsers(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = userDocuments.find(eq("age", 37));
    int numberOfUsers = countUsers(documents);

    assertEquals(2, numberOfUsers);
    assertEquals(2, javalinJackson.fromJsonString(result, User[].class).length);
    assertEquals(numberOfUsers,
       javalinJackson.fromJsonString(result, User[].class).length);
  }

  @Test
  public void canGetUsersWithMultipleParams() {
    mockReq.setQueryString("age=25&company=UMM&name=Chris");
    Context ctx = mockContext("api/users");

    userController.getUsers(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = userDocuments
    .find(and(eq("age", 25), eq("company", "UMM"), eq("name", "Chris")));

    List<Document> docs = intoUserList(documents);
    assertEquals(1, docs.size());
    int numberOfUsers = countUsers(documents);

    assertEquals(1, numberOfUsers);
    assertEquals(1, javalinJackson.fromJsonString(result, User[].class).length);
    assertEquals(numberOfUsers,
       javalinJackson.fromJsonString(result, User[].class).length);
  }

  @Test
  public void canGetTodoWithSpecifiedStatus() {

    mockReq.setQueryString("status=true");
    Context ctx = mockContext("api/todos");

    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = todoDocuments.find(eq("status", true));

    int numberOfTodos = countTodos(documents);

    assertEquals(3, numberOfTodos);
    assertEquals(3, javalinJackson.fromJsonString(result, Todo[].class).length);
    assertEquals(numberOfTodos,
      javalinJackson.fromJsonString(result, Todo[].class).length);
  }

  @Test
  public void canGetTodosWithMultipleParams() {
    mockReq.setQueryString("status=true&category=Software Design");
    Context ctx = mockContext("api/todos");

    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();

    FindIterable<Document> documents = todoDocuments.find(and(eq("status", true), eq("category", "Software Design")));

    int numberOfTodos = countTodos(documents);

    assertEquals(2, numberOfTodos);
    assertEquals(2, javalinJackson.fromJsonString(result, Todo[].class).length);
    assertEquals(numberOfTodos,
      javalinJackson.fromJsonString(result, Todo[].class).length);
  }
}
