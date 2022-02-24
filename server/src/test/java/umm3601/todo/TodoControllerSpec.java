package umm3601.todo;

import static com.mongodb.client.model.Filters.eq;
import static io.javalin.plugin.json.JsonMapperKt.JSON_MAPPER_KEY;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javalin.core.JavalinConfig;
import io.javalin.core.validation.ValidationException;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpCode;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.util.ContextUtil;
import io.javalin.plugin.json.JavalinJackson;

/**
 * Tests the logic of the TodoController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
public class TodoControllerSpec {

  private static final long MAX_REQUEST_SIZE = new JavalinConfig().maxRequestSize;

  private MockHttpServletRequest mockReq = new MockHttpServletRequest();
  private MockHttpServletResponse mockRes = new MockHttpServletResponse();

  private TodoController todoController;

  private ObjectId samsId;

  private static MongoClient mongoClient;
  private static MongoDatabase db;

  private static JavalinJackson javalinJackson = new JavalinJackson();

  @BeforeAll
  public static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
    MongoClientSettings.builder()
    .applyToClusterSettings(builder ->
    builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
    .build());

    db = mongoClient.getDatabase("test");
  }

  @BeforeEach
  public void setUpEach() throws IOException {
    // Reset our mock request and response objects
    mockReq.resetAll();
    mockRes.resetAll();

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
      new Document()
        .append("owner", "Chris")
        .append("category", "Homework")
        .append("status", "Complete")
        .append("body", "Random words for testing"));
    testTodos.add(
      new Document()
        .append("owner", "Lucy")
        .append("category", "Software Design")
        .append("status", "Complete")
        .append("body", "Dog parks are for dogs"));
    testTodos.add(
      new Document()
      .append("owner", "Fernando")
      .append("category", "Homework")
      .append("status", "Incomplete")
      .append("body", "Computers are for humans"));

    samsId = new ObjectId();
    Document sam =
      new Document()
        .append("_id", samsId)
        .append("owner", "Sam")
        .append("category", "Software Design")
        .append("status", "Complete")
        .append("body", "Sam has an id");


    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(sam);

    todoController = new TodoController(db);
  }

  /**
   * Construct an instance of `ContextUtil`, which is essentially
   * a mock context in Javalin. See `mockContext(String, Map)` for
   * more details.
   */
  private Context mockContext(String path) {
    return mockContext(path, Map.of());
  }

  /**
   * Construct an instance of `ContextUtil`, which is essentially a mock
   * context in Javalin. We need to provide a couple of attributes, which is
   * the fifth argument, which forces us to also provide the (default) value
   * for the fourth argument. There are two attributes we need to provide:
   *
   *   - One is a `JsonMapper` that is used to translate between POJOs and JSON
   *     objects. This is needed by almost every test.
   *   - The other is `maxRequestSize`, which is needed for all the ADD requests,
   *     since `ContextUtil` checks to make sure that the request isn't "too big".
   *     Those tests fails if you don't provide a value for `maxRequestSize` for
   *     it to use in those comparisons.
   */
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

  @AfterAll
  public static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @Test
  public void canGetAllTodos() throws IOException {
    // Create our fake Javalin context
    String path = "api/todos";
    Context ctx = mockContext(path);
    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();
    assertEquals(db.getCollection("todos").countDocuments(),
       javalinJackson.fromJsonString(result, Todo[].class).length);
  }


  @Test
  public void canGetTodosWithCategory() throws IOException {

    mockReq.setQueryString("category=Homework");
    Context ctx = mockContext("api/todos");
    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());
    String result = ctx.resultString();

    Todo[] resultTodos = javalinJackson.fromJsonString(result, Todo[].class);

    assertEquals(2, resultTodos.length); // There should be two todos returned
    for (Todo todo : resultTodos) {
      assertEquals("Homework", todo.category);
    }
  }

  @Test
  public void canGetTodosWithStatus() throws IOException {

    mockReq.setQueryString("status=Incomplete");
    Context ctx = mockContext("api/todos");
    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());
    String result = ctx.resultString();
    for (Todo todo : javalinJackson.fromJsonString(result, Todo[].class)) {
      assertEquals("Incomplete", todo.status);
    }
  }

  @Test
  public void canGetTodosWithGivenCategoryAndStatus() throws IOException {

    mockReq.setQueryString("category=Homework&status=Complete");
    Context ctx = mockContext("api/todos");
    todoController.getTodos(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());
    String result = ctx.resultString();
    Todo[] resultTodos = javalinJackson.fromJsonString(result, Todo[].class);

    assertEquals(1, resultTodos.length); // There should be one todo returned
    for (Todo todo : resultTodos) {
      assertEquals("Homework", todo.category);
      assertEquals("Complete", todo.status);
    }
  }

  @Test
  public void canGetTodoWithSpecifiedId() throws IOException {

    String testID = samsId.toHexString();

    Context ctx = mockContext("api/todos", Map.of("id", testID));
    todoController.getTodo(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();
    Todo resultTodo = javalinJackson.fromJsonString(result, Todo.class);

    assertEquals(samsId.toHexString(), resultTodo._id);
    assertEquals("Sam", resultTodo.owner);
  }

  @Test
  public void respondsAppropriatelyToRequestForIllegalId() throws IOException {
    Context ctx = mockContext("api/todos", Map.of("id", "bad"));

    assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriatelyToRequestForNonexistentId() throws IOException {
    Context ctx = mockContext("api/todos/", Map.of("id", "58af3a600343927e48e87335"));

    assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodo(ctx);
    });
  }
/* Add todos
  @Test
  public void canAddTodo() throws IOException {

    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": 25,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";

    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");

    Context ctx = mockContext("api/todos");

    todoController.addNewTodo(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    String result = ctx.resultString();
    String id = javalinJackson.fromJsonString(result, ObjectNode.class).get("id").asText();
    assertNotEquals("", id);
    System.out.println(id);

    assertEquals(1, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(id))));

    //verify todo was added to the database and the correct ID
    Document addedTodo = db.getCollection("todos").find(eq("_id", new ObjectId(id))).first();
    assertNotNull(addedTodo);
    assertEquals("Test Todo", addedTodo.getString("name"));
    assertEquals(25, addedTodo.getInteger("age"));
    assertEquals("testers", addedTodo.getString("company"));
    assertEquals("test@example.com", addedTodo.getString("email"));
    assertEquals("viewer", addedTodo.getString("role"));
    assertTrue(addedTodo.containsKey("avatar"));
  }

  @Test
  public void respondsAppropriateToAddingTodoWithInvalidEmail() throws IOException {
    String testNewTodo = "{"mpany\": \"testers\","
    + "\"email\": \"test@example.com\","
    + "\"role\": \"invalidrole\""
    + "}";
      + "\"company\": \"testers\","
      + "\"email\": \"invalidemail\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithInvalidAge() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": \"notanumber\","
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithAgeOfZero() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": 0,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithNegativeAge() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": -1,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithMissingName() throws IOException {
    String testNewTodo = "{"
      + "\"age\": 25,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithEmptyName() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"\","
      + "\"age\": 25,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithMissingCompany() throws IOException {
    String testNewTodo = "{"
      + "\"age\": 25,"
      + "\"name\": \"Test Todo\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithEmptyCompany() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": 25,"
      + "\"company\": \"\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"viewer\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void respondsAppropriateToAddingTodoWithInvalidRole() throws IOException {
    String testNewTodo = "{"
      + "\"name\": \"Test Todo\","
      + "\"age\": 25,"
      + "\"company\": \"testers\","
      + "\"email\": \"test@example.com\","
      + "\"role\": \"invalidrole\""
      + "}";
    mockReq.setBodyContent(testNewTodo);
    mockReq.setMethod("POST");
    Context ctx = mockContext("api/todos");

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  public void canDeleteTodo() throws IOException {

    String testID = samsId.toHexString();

    // Todo exists before deletion
    assertEquals(1, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));

    Context ctx = mockContext("api/todos", Map.of("id", testID));
    todoController.deleteTodo(ctx);

    assertEquals(HttpCode.OK.getStatus(), mockRes.getStatus());

    // Todo is no longer in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));
  }
  */
}
