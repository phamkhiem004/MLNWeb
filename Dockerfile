# Sử dụng Java 17 (bản nhẹ)
FROM openjdk:17-jdk-slim

# Tạo thư mục làm việc
WORKDIR /app

# Copy tất cả code của bạn vào trong
COPY . .

# Biên dịch file Java
RUN javac Main.java

# Chạy Server
CMD ["java", "Main"]