package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import io.javalin.http.NotFoundResponse;

/**
 * Controller that manCATEGORYs requests for info about todos.
 */
public class TodoController {

  private static final String CATEGORY_KEY = "category";
  private static final String STATUS_KEY = "status";

  private final JacksonMongoCollection<Todo> todoCollection;

  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(database, "todos", Todo.class);
  }

  /**
   * Get the single todo specified by the `id` parameter in the request.
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo object id.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found.");
    } else {
      ctx.json(todo);
    }
  }

  /**
   * Get a JSON response with a list of all the todos.
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with a blank document

    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
        filters.add(eq(CATEGORY_KEY, ctx.queryParam(CATEGORY_KEY)));
    }

    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
        Boolean targetStatus = ctx.queryParamAsClass(STATUS_KEY, Boolean.class).get();
        filters.add(eq(STATUS_KEY, targetStatus));
    }

    // Sort the results. Use the `sortby` query param (default "owner")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");

    ctx.json(todoCollection.find(filters.isEmpty() ? new Document() : and(filters))
      .sort(sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy))
      .into(new ArrayList<>()));
  }

  /**
   * Add a new todo
   * @param ctx
   */
  public void addNewTodo(Context ctx) {
    Todo newTodo = ctx.bodyValidator(Todo.class)
      // Verify that the Todo has a owner that is not blank
      .check(tdo -> tdo.owner != null && tdo.owner.length() > 0, "Todo must have a non-empty Todo owner")
      // Verify that the status is one of the valid status
      .check(tdo -> tdo.status == true || false, "Todo must have a legal Todo status")
      // Verify that the Todo has a body that is not blank
      .check(tdo -> tdo.body != null && tdo.body.length() > 0, "Todo must have a legal body")
      // Verify that the Todo has a category that is not blank
      .check(tdo -> tdo.category != null && tdo.category.length() > 0, "Todo must have a non-empty category owner")
      .get();

    todoCollection.insertOne(newTodo);
    ctx.status(HttpCode.OK);
    ctx.json(Map.of("id", newTodo._id));
  }

  /**
   * Delete the todo specified by the `id` parameter in the request.
   *
   * @param ctx a Javalin HTTP context
   */
  public void deleteTodo(Context ctx) {
    String id = ctx.pathParam("id");
    todoCollection.deleteOne(eq("_id", new ObjectId(id)));
  }

}
