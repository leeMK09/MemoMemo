## 과거 File I/O

- 파일은 디스크에 있고, 네트워크 데이터는 상대방이 보내야 도착한다
- HTTP 요청 body 는 클라이언트가 업로드하는 속도만큼만 들어오고, 압축 결과도 입력을 처리해야 조금씩 나온다

```javascript
const data = await fs.promises.readFile("large-file.bin");
await fs.promises.writeFile("copy.bin", data);
```

- 이 코드는 데이터 전체가 이미 메모리에 있다는 기준이다. 작은 파일이라면 괜찮다
- 하지만 1GB 파일이면 Node 프로세스가 1GB짜리 Buffer를 들고 있어야 한다
- 동시에 요청이 여러 개 들어오면 메모리 사용량이 요청 수에 비례해서 커진다. 여기서 문제는 단순히 메모리를 많이 쓴다는 정도가 아니다
- 큰 Buffer가 많아지고 GC 압박이 커지고, RSS가 증가하고, OS가 메모리 압박을 받으면 컨테이너 환경에서는 OOM Kill까지 갈 수 있다
- Stream은 이 문제를 해결하기 위해 나온 추상화이다
- 공식 문서도 Node Stream을 streaming data를 다루기 위한 추상 인터페이스라고 소개하며 Node의 HTTP API도 큰 요청/응답을 통째로 버퍼링하지 않고 streming할 수 있도록 설계되어 있다고 설명한다
- 즉 Stream은 단순 편의 API가 아니라 큰 데이터와 느린 I/O를 애플리케이션이 메모리 안정적으로 처리하기 위한 런타임 레벨 추상화이다
    - Stream은 데이터 자체가 아닌 데이터가 시간에 따라 조각으로 이동하는 흐름을 다루는 모델

</br>

### Stream 의 처리 흐름

- 애플리케이션 프로세스는 디스크나 네트워크 카드에 직접 접근하지 않는다
- 파일을 열면 OS가 file descriptor를 주고 프로세스는 그 fd를 대상으로 `read`, `write` 같은 syscall을 호출한다
- 네트워크 socket도 마찬가지로 fd처럼 다뤄진다
- 즉 애플리케이션이 파일에서 읽는다, 소켓에 쓴다고 말하지만 실제로는 커널에서 fd를 기준으로 데이터를 옮겨 달라고 요청하는 것이다
- 단순 파일 복사를 raw 하게 생각하면 아래 흐름과 같다

```text
디스크 → 커널 page cache / 커널 buffer → (read syscall) user space buffer → (write syscall) → 커널 page cache / 커널 buffer → 디스크
```

- Node 에서 `fs.createReadStream().pipe(fs.createWriteStream())`를 쓰면 Stream API 를 사용하게 되지만 더 아래에서는 결국 `open`, `read`, `write`, `close`에 가까운 작업들이 반복된다
- 차이는 한 번에 전체 파일을 읽는 게 아니라 적당한 크기의 chunk를 반복해서 읽고 쓴다는 점이다
- 여기서 중요한 트레이드 오프가 나온다
- 작은 chunk로 읽으면 한 번에 들고 있는 메모리는 적지만 syscall 횟수와 이벤트 처리 횟수가 늘어난다
- 큰 chunk로 읽으면 syscall overhead는 줄어들지만 한 번에 버퍼링하는 메모리가 커지고, 느린 destination을 만났을 때 메모리 압박이 커질 수 있다
- 그래서 Stream의 `highWaterMark` 같은 개념이 나온다

</br>

### Buffer 와 Stream

- Buffer는 데이터 조각이다 더 정확히 말하면 바이너리 데이터를 담는 메모리 영역이다
- 반면 Stream은 그 Buffer들이 시간에 따라 흘러가는 통로와 규칙이다
- 그래서 `readable.on("data", chunk => {})`에서 `chunk`가 Buffer인 경우가 많다
- 하지만 그 Buffer 하나가 파일의 한 줄이나 HTTP 메시지 하나나 TCP 패킷 하나를 의미하지는 않는다
- Stream은 byte 흐름을 chunk 단위로 전달할 뿐 chunck 경계는 OS 버퍼 상태, Node 내부 버퍼 상태, highWaterMark, 네트워크 상황, 처리 속도에 따라 달라진다
- 그래서 CSV 한 줄, JSON 하나, TCP 메시지 하나, 한글 문자 하나가 chunck 경계와 정확히 맞는다는 보장은 없다
- TCP는 message protocol이 아니라 byte stream이기 때문이다 그래서 애플리케이션 레벨에서는 반드시 framing이 필요하다
- 줄 단위 프로토콜이면 `\n` 을 기준으로 자르고 바이너리 프로토콜이면 length-prefix를 붙이고 HTTP/2 나 gRPC는 자체 frame 구조를 둔다

</br>

### Readable Stream

- Readable Stream 은 데이터 source를 조각 단위로 읽을 수 있게 만든 추상화이다
- 파일을 예로 들면 source는 디스크 파일이다
- HTTP 요청에서는 client가 보내는 request body가 source이다
- TCP socket에서는 상대방이 보낸 byte stream이 source이다 gzip decompress 결과도 source가 될 수 있다
- Readable 이 필요한 이유는 source의 생산 속도와 consumer의 처리 속도가 다르기 때문이다
    - 파일을 빠르게 읽을 수 있는데, 내가 처리하는 로직이 느릴 수 있다
    - 네트워크는 반대로 상대방이 천천히 보낼 수 있다
    - 데이터가 무한히 들어오는 로그나 socket이라면 끝이 없을 수도 있다
    - 그래서 Readable은 데이터가 있으면 전부 던져준다가 아니라 내부적으로 버퍼를 두고 consumer가 따라올 수 있는 만큼만 데이터를 노출해야 한다
- Readable에는 크게 두 사고방식이 있다
- 하나는 flowing mode이다 `data` 이벤트를 붙이면 데이터가 들어오는 대로 흘러온다

```javascript
readable.on("data", (chunk) => {
    // chunk 처리
});
```

- 이 방식은 간단하지만, 처리 로직이 비동기적으로 느릴 때 조심해야 한다
- `data` 이벤트 안에서 async 작업을 한다고 해서 Readable이 자동으로 그 Promise를 기다려주는 것은 아니다 그러면 처리 중인 작업이 계속 쌓일 수 있다
- 다른 하나는 pull 에 가까운 방식이다

```javascript
for await (const chunk of readable) {
    await processChunk(chunk);
}
```

- 이 방식은 chunk 하나 처리 후 다음 chunk 라는 흐름이 더 명확하다
- 그래서 대용량 처리에서는 async iterator 방식이 이해하기 쉽고 안전한 경우가 많다
- Readable의 트레이드 오프는 단순하다 자동으로 흐르게 하면 처리량은 좋아질 수 있지만 소비사 속도 제어가 어려워진다
- 소비자가 직접 당겨가게 하면 제어는 쉬워지지만 구현 방식에 따라 처리량이 낮아질 수 있다
- 그래서 실무에서는 source가 빠르고 처리 로직이 무거우면 async iterator나 pipeline을 사용하고 단순 전달이면 pipe/pipeline을 쓰는 식으로 선택한다

</br>

### Writable Stream

- Writable Stream은 데이터 destination에 조각 단위로 쓸 수 있게 만든 추상화이다
- 파일 쓰기, HTTP 응답, TCP socket 쓰기, stdout, gzip 입력, S3 업로드 destination 같은 것이 Writable에 해당한다
- Writable에서 가장 중요한 건 `write()`의 반환값이다

```javascript
const ok = writable.write(chunk);
```

- `ok`가 `true`면 지금은 계속 써도 된다는 의미이고, `false`면 내부 버퍼가 highWaterMark에 도달했으니 `drain`을 기다리라는 의미이다
- Node 공식 문서도 Writable 내부 버퍼가 highWaterMark에 도달하거나 넘으면 `write()` 가 `false`를 반환하고, 이 경우 추가 쓰기 전에 `drain`을 기다리는 의미라고 설명한다
- 이게 왜 필요하냐면 destination은 항상 빠르지 않기 때문이다
- 예를 들어 서버가 파일을 읽어서 클라이언트에게 다운로드시킨다고 하자
- 서버 디스크는 빠른데 클라이언트 네트워크가 느리면 서버가 파일을 계속 읽어서 socket에 쓰려고 해도 kernel socket send buffer가 언젠가 찬다
- 그 상태에서 애플리케이션이 계속 write하면 Node 내부 메모리에 chunk 가 계속 쌓ㄷ다 인
- 그러면 Stream을 썼는데도 메모리가 터질 수 있다
- Stream의 핵심은 chunking 자체가 아니라 backpressure를 지키는 것 이다
- 수동으로 Writable에 쓸 때는 이런식으로 사용해야 한다

```javascript
import { once } from "node:events";

async function writeAll(writable, chunks) {
    for (const chunk of chunks) {
        const ok = writable.write(chunk);

        if (!ok) {
            await once(writable, "drain");
        }
    }

    writable.end();
}
```

- 이 코드는 단순해 보이지만 매우 중요하다
- `write()` 가 false를 반환했는데 무시하면 consumer가 느리다는 신호를 무시하고 producer가 계속 밀어붙이는 것이다
- 그 순간 Stream의 장점이 사라진다

</br>

### Backpressure 는 Stream 의 핵심

- Stream의 본질을 고르라면 backpressure이다
- Backpressure는 생산자가 소비자보다 빠를 때, 소비자가 생산자에게 더 보내지 말라고 압력을 되돌려 보내는 구조이다

```text
Producer 가 빠름 → Writable 내부 buffer 증가 → highWaterMark 도달 → write() false → Readable pause 또는 생산 중단 → destination이 비워짐 → drain → 다시 resume
```

- 이 구조가 없으면 Stream은 단순히 이벤트 전달기에 불과하다 source가 빠르면 메모리는 계속 증가한다
- OS에도 비슷한 흐름제어가 있다 TCP 에서는 수신 윈도우가 있고 kernel socket buffer가 있으며, pipe에도 커널 버퍼가 있다
- OS pipe에서 buffer가 꽉 차면 write가 막히거나 non-blocking이면 실패한다
- Node Stream은 이런 raw-level 흐름 제어를 JS 레벨에서 다루기 쉽게 추상화한 것 이다
- 여기서 중요한 건 backpressure가 만능은 아니라는 점이다
- Stream backpressure는 보통 하나의 piepline 안에서 producer 와 consumer 사이의 속도 차이를 제어한다
- 하지만 서비스 전체 부하를 제어하지는 못한다
- 예를 들어 다운로드 요청이 10,000개 들어오면 각 요청의 Stream이 backpressure를 잘 지켜도 서버 전체 fd, 메모리, 네트워크 bandwidth, CPU는 고갈될 수 있다
- 그래서 실무에서는 Stream 만으로 해결하지 않고 connection limit, timeout, rate limit, load balancer, CDN, object storage direct download 같은 인프라를 함께 사용한다
- 즉 Stream은 한 흐름 내부의 압력 제어이고 인프라는 전체 시스템의 유입량 제어이다 둘은 다른 레벨의 해결책이다

</br>

### pipe

- `pipe()`는 Readable의 출력이 Writable의 입력으로 들어가는 구조를 쉽게 만들기 위해 나왔다

```javascript
fs.createReadStream("input").pipe(fs.createWriteStream("output"));
```

- 이 구조가 필요한 이유는 OS 의 pipe 와 비슷하다
    - `cat access.log | grep ERROR | gzip > error.gz`
- 앞 단계의 출력이 다음 단계의 입력이 된다, Node의 pipe 도 같은 사고 방식이다. 파일 읽기 결과를 압축기에 넣고 압축 결과를 파일 writer에 넣는다

```javascript
fs.createReadStream("access.log")
    .pipe(zlib.createGzip())
    .pipe(fs.createWriteStream("access.log.gz"));
```

- 다만 Node의 `.pipe()`는 OS pipe 그 자체가 아니다
- OS pipe는 커널 pipe buffer를 가진 fd 간 연결이고, Node pipe는 JS Stream 객체 사이의 연결이다
- 하지만 둘 다 본질적으로는 중간에 전체 데이터를 저장하지 않고 흐름을 연결한다는 개념을 공유한다
- pipe의 장점은 간단함과 backpressure 자동 연결이다
- destination이 느려지면 Readable을 pause하고, destination이 drain되면 다시 resume 한다
- 하지만 pipe의 한계는 에러처리이다
- 여러 Stream을 연결했을 때 중간 Transform에서 에러가 났는지, source에서 에러가 났는지, destination에서 에러가 났는지, 이미 열린 fd를 닫아야 하는지, HTTP response를 어떻게 끝내야 하는지 등을 직접 다루기 어렵다
- 그래서 pipeline이 나왔다

</br>

### pipeline

- `pipeline()`은 pipe chain의 에러 전파와 cleanup 문제를 해결하기 위해 나온 API 다

```javascript
import { pipeline } from "node:stream/promises";

await pipeline(
    fs.createReadStream("input.tar"),
    zlib.createGzip(),
    fs.createWriteStream("input.tar.gz"),
);
```

- `pipeline()` 은 단순히 pipe를 예쁘게 감싼 것이 아니다
- 중간 Stream에서 에러가 나면 관련 Stream들을 destroy하고, 완료/실패를 Promise나 callback으로 알려준다
- Node 문서도 `stream.pipeline()`이 에러 시 대부분의 stream에 `destroy(err)`를 호출한다고 설명한다
- 이 API가 나온 이유는 Stream chain이 길어질수록 lifecycle 관리가 어려워지기 때문이다

```text
Readable open → Transform1 처리 중 → Transform2에서 error → Writable은 열려 있음 → Readable도 계속 읽을 수 있음 → fd/socket/resource leak 가능
```

- pipeline은 위 같은 실수를 줄여준다
- 하지만 pipeline도 트레이드 오프는 존재한다
- 에러 시 destroy를 적극적으로 호출하기 때문에 HTTP response처럼 에러 응답을 예쁘게 내려야 하는 상황에서는 조심해야 한다
- 이미 response body를 쓰는 중이었다면 socket이 닫힐 수 있고, 클라이언트는 중간에 연결 종료를 볼 수 있다
- 그래서 파일 압축, 파일 변환, 배치 ETL 같은 곳에서는 pipeline이 좋고, HTTP 응답에서는 상황에 따라 직접 error handling을 섬세하게 하는 편이 낫다

</br>

### 4가지 Stream (Readable, Writable, Duplex, Transform)

- Node Stream이 4가지로 나뉜 이유는 실제 I/O 리소스의 성격이 다르기 때문이다
- Node 공식 문서도 기본 Stream 타입을 Writable, Readable, Duplex, Transform으로 구분한다
- Readable은 source이다. 파일 읽기, HTTP request body, socket의 incoming data처럼 읽을 수 있는 쪽 이다
- Writable은 destination이다. 파일 쓰기, HTTP response, socket의 outgoing data처럼 쓸 수 있는 쪽 이다
- Duplex는 읽기와 쓰기가 둘 다 있는 리소스다
    - TCP socket이 대표적이다
    - 클라이언트가 서버에게 데이터를 보내면 서버 입장에서는 readable이고, 서버가 클라이언트에게 응답을 보내면 writable이다
    - 그런데 이 둘은 논리적으로 독립적이다
    - 읽기 쪽이 끝나도 쓰기 쪽이 남아 있을 수 있고, 쓰기 쪽이 막혀도 읽기 쪽은 이벤트가 올 수 있따
- Transform은 Duplex 의 특수한 형태이다
    - 쓰기 쪽으로 들어온 데이터를 변환해서 읽기 쪽으로 내보낸다
    - gzip, crypto cipher, line parser, CSV parser, JSONL parser 같은 것들이 여기에 해당한다

</br>

### Transform Stream

- Transform 은 Stream 의 조합성을 만들어준다
- 파일을 읽어서 바로 쓰는 건 단순 복사이다. 하지만 실무에서는 중간에 뭔가를 해야하는 일이 많다

```text
업로드 body → 압축 해제 → 파일 검증 → CSV row 파싱 → 검증 → DB batch insert
```

- 이걸 전체 파일을 메모리에 올려서 처리하면 단순하지만 위험하다
- Transform을 쓰면 각 단계를 chunk 단위로 연결할 수 있다
- 그런데 Transform에는 큰 함정이 있다. Transform 안의 작업이 CPU-bound 이면 event loop를 막을 수 있다
- 예를 들어 gzip 압축, 큰 JSON parse, 이미지 리사이징, 암호화, 복잡한 정규식 처리 같은건 CPU를 많이 쓴다
- Stream으로 chunk를 나눴다고 해서 CPU 비용이 사라지는건 아니기 때문에 오히려 모든 chunk를 JS main thread에서 처리하면 event loop가 바빠져서 다른 요청의 latency가 튈 수 있다
- 그래서 실무에서는 가벼운 변환, 예를 들어 line split, 간단한 필드 변환, 헤더 제거 같은 것은 Transform으로 충분하다
- 반면 압축률 높은 압축, 이미지 처리, 대용량 암호화, 복잡한 파싱은 worker_threads, child_process, 별도 Go/Rust 서비스, 큐 기반 비동기 처리로 넘기는 게 낫다
- 그리고 정적 파일 압축은 요청 시점에 하지 말고 미리 gzip/brotil 파일을 만들어 CDN이나 Nginx에서 처리하는 편이 더 낫다
