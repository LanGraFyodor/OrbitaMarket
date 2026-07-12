import type { CapturedFrame } from "./SatelliteMissionMap";

const DATABASE_NAME = "orbitamarket-products";
const DATABASE_VERSION = 1;
const STORE_NAME = "frames";

interface StoredFrame {
  orderId: string;
  blob: Blob;
  captured_at: string;
  center: [number, number];
  zoom: number;
  source: string;
}

const openDatabase = () =>
  new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(STORE_NAME)) {
        database.createObjectStore(STORE_NAME, { keyPath: "orderId" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });

const runTransaction = async <T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
) => {
  const database = await openDatabase();
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, mode);
      const request = operation(transaction.objectStore(STORE_NAME));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      transaction.onerror = () => reject(transaction.error);
    });
  } finally {
    database.close();
  }
};

const toCapturedFrame = (stored: StoredFrame): CapturedFrame => ({
  image: URL.createObjectURL(stored.blob),
  captured_at: stored.captured_at,
  center: stored.center,
  zoom: stored.zoom,
  source: stored.source,
});

export const loadCapturedFrame = async (orderId: string) => {
  const stored = await runTransaction<StoredFrame | undefined>(
    "readonly",
    (store) => store.get(orderId),
  );
  return stored ? toCapturedFrame(stored) : null;
};

export const hasCapturedFrame = async (orderId: string) =>
  (await runTransaction<number>("readonly", (store) => store.count(orderId))) > 0;

export const saveCapturedFrame = async (
  orderId: string,
  blob: Blob,
  metadata: Omit<CapturedFrame, "image">,
) => {
  const stored: StoredFrame = { orderId, blob, ...metadata };
  await runTransaction("readwrite", (store) => store.put(stored));
  return toCapturedFrame(stored);
};

export const frameFromBlob = (
  blob: Blob,
  metadata: Omit<CapturedFrame, "image">,
): CapturedFrame => ({
  image: URL.createObjectURL(blob),
  ...metadata,
});
