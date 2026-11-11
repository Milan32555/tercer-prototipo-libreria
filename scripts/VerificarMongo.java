import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifica en MongoDB lo que pide la rubrica (lo mismo que se revisaria con mongosh):
 * que existan las colecciones libros y ejemplares, sus indices, y que ejemplares.libroId sea un
 * ObjectId que apunta a libros._id (sin referencias huerfanas).
 *
 * Uso (desde la raiz del repositorio, con el driver que descarga Maven):
 *   scripts\verificar-mongo.cmd [base]          (por defecto la base "libreria_evaluacion")
 */
public class VerificarMongo {

    public static void main(String[] args) {
        String base = args.length > 0 ? args[0] : "libreria_evaluacion";
        try (MongoClient cliente = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase db = cliente.getDatabase(base);
            System.out.println("Base: " + base);
            System.out.println("Colecciones: " + db.listCollectionNames().into(new ArrayList<>()));

            for (String nombre : List.of("libros", "ejemplares")) {
                MongoCollection<Document> coleccion = db.getCollection(nombre);
                System.out.printf("%n== %s: %d documentos%n", nombre, coleccion.countDocuments());
                for (Document indice : coleccion.listIndexes()) {
                    System.out.printf("   indice %-16s %s%s%n", indice.getString("name"), indice.get("key", Document.class).toJson(),
                            indice.getBoolean("unique", false) ? "  (unico)" : "");
                }
            }

            Document ejemplar = db.getCollection("ejemplares").find(new Document("codigo", "EJ-001")).first();
            if (ejemplar == null) {
                ejemplar = db.getCollection("ejemplares").find().first();
            }
            if (ejemplar != null) {
                Object libroId = ejemplar.get("libroId");
                System.out.printf("%nEjemplar %s:%n   %s%n", ejemplar.getString("codigo"), ejemplar.toJson());
                System.out.printf("   libroId es %s%n", libroId == null ? "null" : libroId.getClass().getSimpleName());
                Document libro = db.getCollection("libros").find(new Document("_id", libroId)).first();
                System.out.printf("   libros._id = libroId -> %s%n", libro == null ? "NO EXISTE" : libro.getString("isbn") + " " + libro.getString("titulo"));
            }

            // Referencias huerfanas: ejemplares cuyo libroId no esta en libros (equivale a un $lookup vacio).
            List<Document> huerfanos = db.getCollection("ejemplares").aggregate(List.of(
                    new Document("$lookup", new Document("from", "libros").append("localField", "libroId")
                            .append("foreignField", "_id").append("as", "libro")),
                    new Document("$match", new Document("libro", new Document("$size", 0))),
                    new Document("$count", "cantidad"))).into(new ArrayList<>());
            long noObjectId = db.getCollection("ejemplares").countDocuments(
                    new Document("libroId", new Document("$not", new Document("$type", "objectId"))));
            System.out.printf("%nEjemplares con libroId que no es ObjectId: %d%n", noObjectId);
            System.out.printf("Ejemplares sin libro (referencias huerfanas): %d%n",
                    huerfanos.isEmpty() ? 0 : huerfanos.get(0).getInteger("cantidad"));
            boolean bien = noObjectId == 0 && huerfanos.isEmpty() && (ejemplar == null || ejemplar.get("libroId") instanceof ObjectId);
            System.out.println(bien ? "OK: colecciones e indices listos; las referencias existentes son ObjectId y apuntan a libros._id" : "REVISAR: hay problemas");
            System.exit(bien ? 0 : 1);
        }
    }
}
