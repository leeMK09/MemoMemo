import http from 'k6/http';

export const options = {
  scenarios: {
    create_orders: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '10s',
    },
  },
};

export default function () {
  const url = 'http://localhost:8080/orders/create';
  const payload = JSON.stringify({
    inventoryId: 1,
    quantity: 1
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(url, payload, params);
}
