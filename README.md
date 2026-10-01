# Post-contenido — Unidad 7: Patrones Arquitectónicos I

**Estudiante:** Juan Sebastián Gamboa Cristancho  
**Asignatura:** Patrones de Diseño de Software — Universidad de Santander (UDES)

## Descripción

Repositorio del post-contenido de la Unidad 7. Contiene un único proyecto Spring Boot,
`multas-biblioteca-api`, que gestiona las multas de una biblioteca universitaria: la biblioteca
registra una multa cuando un estudiante devuelve un libro con retraso, el sistema calcula su
monto y permite marcarla como pagada.

- **Parte 1:** API REST con arquitectura en capas (Model, Repository, Service, Controller) sobre H2.
- **Parte 2:** pago en línea de multas con dos pasarelas intercambiables (en construcción).

## Parte 1 — Arquitectura en Capas

Cada capa vive en su propio paquete y solo depende de la capa inmediatamente inferior:
el controlador nunca accede al repositorio, siempre pasa por `MultaService`.

| Capa | Paquete | Clases | Responsabilidad |
|---|---|---|---|
| Presentación | `controller/` | `MultaController`, `GenerarMultaRequest`, `GlobalExceptionHandler` | Recibe HTTP, valida la entrada con Bean Validation y traduce excepciones a códigos 400, 404 y 409. No contiene reglas de negocio. |
| Aplicación | `service/` | `MultaService` | Orquesta los casos de uso (generar, consultar, pagar en ventanilla), aplica el tope de 3 multas pendientes y delimita las transacciones con `@Transactional`. |
| Dominio | `model/` | `Multa`, `EstadoMulta`, excepciones de negocio | Entidad con comportamiento propio: `calcularMonto` (días × $500, tope $15.000) y `marcarComoPagada`. |
| Infraestructura | `repository/` | `MultaRepository` | Acceso a datos con Spring Data JPA, incluida la consulta agregada `countByEstudianteIdAndEstado`. |

### Estructura de paquetes

```
gamboa-post1-u7/
├── README.md
├── docs/capturas/                      ← evidencia de los endpoints probados
└── multas-biblioteca-api/
    ├── pom.xml
    └── src/main/java/com/example/multas/
        ├── MultasBibliotecaApiApplication.java
        ├── controller/                 ← Presentación (@RestController)
        │   ├── MultaController.java
        │   ├── GenerarMultaRequest.java
        │   └── GlobalExceptionHandler.java
        ├── service/                    ← Aplicación (@Service)
        │   └── MultaService.java
        ├── model/                      ← Dominio (entidad y excepciones)
        │   ├── Multa.java
        │   ├── EstadoMulta.java
        │   ├── MultaNotFoundException.java
        │   ├── LimiteMultasPendientesException.java
        │   └── MultaYaPagadaException.java
        └── repository/                 ← Infraestructura (@Repository)
            └── MultaRepository.java
```

### Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/multas` | Lista todas las multas | 200 |
| GET | `/api/multas/{id}` | Busca una multa por ID | 200, 404 |
| GET | `/api/multas/estudiante/{estudianteId}` | Lista las multas de un estudiante | 200 |
| POST | `/api/multas` | Genera una multa y calcula su monto | 201, 400, 409 |
| PATCH | `/api/multas/{id}/pagar` | Paga una multa en ventanilla | 200, 404, 409 |

## Cómo ejecutar

Requisitos: Java 17 o superior y Maven 3.8+ (o el wrapper `./mvnw` incluido).

```bash
cd multas-biblioteca-api
mvn clean package
mvn spring-boot:run
```

La API queda en `http://localhost:8080/api/multas` y la consola H2 en
`http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:multas_biblioteca_db`, usuario `sa`, sin contraseña).

Ejemplo de creación de una multa:

```bash
curl -i -X POST http://localhost:8080/api/multas \
  -H "Content-Type: application/json" \
  -d '{"estudianteId":"EST-001","concepto":"Devolucion tardia de Clean Code","diasAtraso":5}'
```

## Evidencia de pruebas — Parte 1

**1. Lista vacía al iniciar — 200 OK**  
![GET lista vacía](docs/capturas/01-get-lista-vacia.png)

**2. Creación de una multa con monto calculado (5 días × $500 = $2.500) — 201 Created**  
![POST crear](docs/capturas/02-post-crear-201.png)

**3. Datos inválidos (sin estudianteId y 0 días) — 400 Bad Request**  
![POST validación](docs/capturas/03-post-validacion-400.png)

**4. Cuarta multa pendiente para el mismo estudiante — 409 Conflict**  
![POST límite](docs/capturas/04-post-limite-409.png)

**5. ID inexistente — 404 Not Found**  
![GET no encontrada](docs/capturas/05-get-no-encontrada-404.png)

**6. Pago en ventanilla — 200 OK, estado PAGADA y método VENTANILLA**  
![PATCH pagar](docs/capturas/06-patch-pagar-200.png)

**7. Pago repetido de la misma multa — 409 Conflict**  
![PATCH repetido](docs/capturas/07-patch-repetido-409.png)

## Herramientas utilizadas

- Java 17 (compilación), Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, H2
- Apache Maven, curl, Git, GitHub

## Decisiones de diseño

### Punto de decisión 1 — Cálculo del monto: ¿entidad o Service?

`Multa.calcularMonto` vive en la propia entidad. El criterio aplicado fue: **si una regla no necesita
ningún colaborador externo (Repository, otro Service, configuración), pertenece al objeto de dominio**.
El monto depende únicamente de los días de atraso, así que es una regla que describe cómo se comporta
una multa, no cómo se orquesta un caso de uso.

La alternativa —un método privado en `MultaService`— también funcionaría, pero dejaría a `Multa` como
un contenedor de datos sin comportamiento (modelo de dominio anémico). Cualquier otro punto del sistema
que necesite recalcular un monto, por ejemplo un proceso de reliquidación, tendría que duplicar la
fórmula o pasar por el Service para algo que no requiere dependencias. Además, en la entidad la regla
se prueba con JUnit puro, sin levantar el contexto de Spring. Lo mismo aplica a `marcarComoPagada`: la
entidad protege su propia invariante (no pagar dos veces) en lugar de confiar en que cada llamador la
verifique.

### Punto de decisión 2 — Conteo de multas pendientes: ¿consulta o filtrado en memoria?

El tope de 3 multas pendientes sí necesita datos que solo conoce la base de datos. La **decisión**
("¿se permite generar una multa más?") la toma `MultaService`, pero el **dato** ("¿cuántas tiene
pendientes?") se resuelve con `countByEstudianteIdAndEstado`, que Spring Data JPA traduce a un
`SELECT COUNT(*) ... WHERE estudiante_id = ? AND estado = ?`.

La alternativa —`findByEstudianteId` y filtrar con `stream().filter(...)`— traería a memoria todo el
historial del estudiante para quedarse con un número. Con pocos datos de prueba no hay diferencia
visible, pero con miles de multas por estudiante cada creación cargaría y mapearía miles de entidades:
el tiempo de respuesta y el consumo de memoria crecerían en proporción al historial, mientras que el
`COUNT` devuelve siempre una sola fila y puede apoyarse en un índice sobre `(estudiante_id, estado)`.

Limitación conocida: dos solicitudes simultáneas para el mismo estudiante podrían leer el mismo conteo
y superar el tope. Para este alcance se acepta; en producción se resolvería con un bloqueo pesimista o
una restricción en base de datos.
