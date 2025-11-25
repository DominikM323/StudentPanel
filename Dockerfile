# 1. Wybieramy oficjalny obraz JDK
FROM maven:3.9.4-eclipse-temurin-21-alpine

# 2. Ustawiamy katalog roboczy w kontenerze
WORKDIR /app

# 3. Kopiujemy pliki Maven

COPY pom.xml .

# 4. Pobieramy zależności (layer caching)

RUN mvn dependency:go-offline -B

# 5. Kopiujemy resztę źródeł
COPY src ./src
COPY data/test.mv.db /app/data/test.mv.db

# 6. Budujemy aplikację
RUN mvn clean package -DskipTests

# 7. Ustawiamy komendę startową
CMD ["java", "-jar", "target/sw-0.0.1-SNAPSHOT.jar"]