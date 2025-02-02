# Ứng dụng Theo Dõi Trẻ Em

Ứng dụng Android (viết bằng Kotlin) giám sát trẻ em với các chức năng realtime, tích hợp Firebase FCM v1, Google Maps API và nhiều công nghệ khác. Dự án này là nội dung của quá trình thực hiện học phần nghiên cứu tốt nghiệp tại trường.

## Chức năng chính

- **Theo dõi vị trí realtime:**  
  Hiển thị vị trí của trẻ trên bản đồ sử dụng Google Maps API.
  
- **Nghe âm thanh realtime:**  
  Giám sát âm thanh xung quanh thông qua Firebase Realtime Database, với cơ chế truyền dữ liệu bằng cách chia âm thanh thành các chunk nhỏ.

- **Xem thời gian sử dụng thiết bị:**  
  Ghi nhận và hiển thị thời gian sử dụng của thiết bị thông qua API `UsageStatsManager`.

- **Màn hình chặn ứng dụng:**  
  Hiển thị overlay thông qua `WindowManager` để chặn giao diện của ứng dụng khi trẻ vượt quá thời gian sử dụng cho phép.

- **Chặn từ khóa nhạy cảm:**  
  Sử dụng Android Accessibility Service để ngăn chặn trẻ tìm kiếm các từ khóa không phù hợp.

- **Tạo và giao nhiệm vụ:**  
  Phụ huynh có thể tạo các nhiệm vụ; nhiệm vụ này sẽ được hiển thị dưới dạng thông báo cho trẻ nhỏ, giúp hướng dẫn hoặc nhắc nhở việc thực hiện nhiệm vụ.

## Một vài hình ảnh minh họa cho ứng dụng

### 1. Theo dõi vị trí realtime
<img src="https://github.com/user-attachments/assets/a2d22d2d-970d-4f03-89dc-b60e70226ae3" alt="Theo dõi vị trí realtime" width="300"/>

### 2. Tạo và giao nhiệm vụ
<img src="https://github.com/user-attachments/assets/0ad197a3-2787-43b5-983f-e6528d3f0301" alt="Màn hình thông báo bên phía trẻ em" width="300"/>

<img src="https://github.com/user-attachments/assets/3dff692e-f45e-4259-ba28-28670699bd3a" alt="Màn hình hiển thị danh sách nhiệm vụ bên phía trẻ em" width="300"/>

### 3. Màn hình chặn ứng dụng
<img src="https://github.com/user-attachments/assets/fe964b20-9e99-4fdc-b52a-08c6d614cd4c" alt="Màn hình chặn - Bên phía trẻ em" width="300"/> 

<img src="https://github.com/user-attachments/assets/8f12b1ef-b328-4634-94b7-f7e0c7ddf3ee" alt="Màn hình chặn - Bên phía phụ huynh" width="300"/> 

## Công nghệ sử dụng

- **Ngôn ngữ lập trình:** Kotlin
- **Realtime Communication:** Firebase FCM v1, Firebase Realtime Database
- **Bản đồ:** Google Maps API
- **Accessibility:** Android Accessibility Service
