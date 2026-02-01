# Sử dụng Java 17 bản chuẩn (Eclipse Temurin)
FROM eclipse-temurin:17-jdk-alpine

# Copy toàn bộ code vào thư mục /app
COPY . /app

# Đặt thư mục làm việc
WORKDIR /app

# Biên dịch file code
RUN javac Main.java

# Chạy file code
CMD ["java", "Main"]