# Bộ ví dụ thực hành môn Hệ Điều Hành (Operating Systems)

Tất cả file đều bằng C, đã được **biên dịch và chạy thử thành công** trên Linux (Ubuntu/gcc 13).

## Danh sách file

| File | Chủ đề |
|---|---|
| `01_fork_exec_wait.c` | Quản lý tiến trình: fork, exec, wait |
| `02_pipe_unnamed.c` | IPC: Unnamed Pipe (cha-con) |
| `03_fifo_writer.c` + `03_fifo_reader.c` | IPC: Named Pipe / FIFO (2 tiến trình độc lập) |
| `04_shared_memory.c` | IPC: Shared Memory (System V) |
| `05_message_queue.c` | IPC: Message Queue (System V) |
| `06_pthread_basic.c` | Đa luồng cơ bản với Pthread |
| `07_producer_consumer.c` | Đồng bộ: Producer-Consumer (Semaphore + Mutex) |
| `08_dining_philosophers.c` | Đồng bộ: Dining Philosophers (chống Deadlock) |
| `09_reader_writer.c` | Đồng bộ: Reader-Writer Problem |
| `10_cpu_scheduling.c` | Lập lịch CPU: FCFS, SJF, Round Robin |
| `11_page_replacement.c` | Quản lý bộ nhớ: FIFO, LRU, Optimal (Page Replacement) |

## Cách biên dịch & chạy

### Các file IPC / Process cơ bản (không cần thread)
```bash
gcc 01_fork_exec_wait.c -o fork_demo
./fork_demo

gcc 02_pipe_unnamed.c -o pipe_demo
./pipe_demo

gcc 04_shared_memory.c -o shm_demo
./shm_demo

gcc 05_message_queue.c -o mq_demo
./mq_demo
```

### FIFO (Named Pipe) — cần MỞ 2 TERMINAL riêng biệt
```bash
# Terminal 1:
gcc 03_fifo_reader.c -o fifo_reader
./fifo_reader

# Terminal 2 (mở song song):
gcc 03_fifo_writer.c -o fifo_writer
./fifo_writer
```
Gõ nội dung ở terminal writer, terminal reader sẽ nhận được. Gõ `exit` để thoát cả hai.

### Các file dùng Thread/Semaphore (cần link `-lpthread`)
```bash
gcc 06_pthread_basic.c -o thread_demo -lpthread
./thread_demo

gcc 07_producer_consumer.c -o pc_demo -lpthread
./pc_demo

gcc 08_dining_philosophers.c -o dining_demo -lpthread
./dining_demo

gcc 09_reader_writer.c -o rw_demo -lpthread
./rw_demo
```

### Mô phỏng thuật toán (không cần thư viện thêm)
```bash
gcc 10_cpu_scheduling.c -o scheduling_demo
./scheduling_demo

gcc 11_page_replacement.c -o page_demo
./page_demo
```

## Ghi chú quan trọng

- **Pipe (unnamed)**: chỉ dùng được giữa cha-con (cùng `fork()`). Luôn nhớ `close()` đầu không dùng để tránh treo chương trình.
- **FIFO (named pipe)**: dùng được giữa các tiến trình độc lập. `open()` ở chế độ blocking sẽ **chờ** cho đến khi cả 2 đầu (đọc + ghi) đều mở.
- **Shared Memory / Message Queue**: dùng `ipcs` để xem các vùng IPC đang tồn tại, `ipcrm` để xóa thủ công nếu chương trình bị crash giữa chừng (`ipcrm -m <id>` hoặc `ipcrm -q <id>`).
- **Producer-Consumer / Dining Philosophers / Reader-Writer**: đây là 3 bài toán đồng bộ hay ra **thi tự luận viết giả mã** lẫn **bài tập lập trình**. Hiểu rõ vai trò của `sem_wait()`/`sem_post()` và vị trí đặt `mutex_lock`/`unlock` là mấu chốt.
- **Scheduling / Page Replacement**: dữ liệu mẫu (mảng `processes1[]`, mảng `pages[]`) nằm trong hàm `main()` — chỉ cần sửa giá trị để áp dụng cho đề bài cụ thể của bạn.

## Sửa dữ liệu theo đề bài của bạn

- File `10_cpu_scheduling.c`: sửa mảng `processes1[]` (định dạng `{id, arrival, burst}`).
- File `11_page_replacement.c`: sửa mảng `pages[]` và biến `num_frames`.
