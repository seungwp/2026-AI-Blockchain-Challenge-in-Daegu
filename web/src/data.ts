import index from '../../contracts/sample/index.json';
import stores from '../../contracts/sample/stores.json';
import prices from '../../contracts/sample/prices.json';
import validation from '../../contracts/sample/validation.json';
import s1 from '../../contracts/sample/sales/S1.json';
import s2 from '../../contracts/sample/sales/S2.json';
import s3 from '../../contracts/sample/sales/S3.json';
import s4 from '../../contracts/sample/sales/S4.json';
import s5 from '../../contracts/sample/sales/S5.json';
import s6 from '../../contracts/sample/sales/S6.json';
import a0629s1 from '../../contracts/sample/advice/2026-06-29/S1.json';
import a0629s2 from '../../contracts/sample/advice/2026-06-29/S2.json';
import a0629s3 from '../../contracts/sample/advice/2026-06-29/S3.json';
import a0629s4 from '../../contracts/sample/advice/2026-06-29/S4.json';
import a0629s5 from '../../contracts/sample/advice/2026-06-29/S5.json';
import a0629s6 from '../../contracts/sample/advice/2026-06-29/S6.json';
import a0914s1 from '../../contracts/sample/advice/2026-09-14/S1.json';
import a0914s2 from '../../contracts/sample/advice/2026-09-14/S2.json';
import a0914s3 from '../../contracts/sample/advice/2026-09-14/S3.json';
import a0914s4 from '../../contracts/sample/advice/2026-09-14/S4.json';
import a0914s5 from '../../contracts/sample/advice/2026-09-14/S5.json';
import a0914s6 from '../../contracts/sample/advice/2026-09-14/S6.json';

export type Json = Record<string, any>;
export const appIndex = index as Json;
export const storeList = stores as Json[];
export const priceData = prices as Json;
export const validationData = validation as Json;
export const adviceData: Record<string, Record<string, Json>> = {
  '2026-06-29': { S1: a0629s1, S2: a0629s2, S3: a0629s3, S4: a0629s4, S5: a0629s5, S6: a0629s6 },
  '2026-09-14': { S1: a0914s1, S2: a0914s2, S3: a0914s3, S4: a0914s4, S5: a0914s5, S6: a0914s6 },
};
export const salesData: Record<string, Json> = { S1: s1, S2: s2, S3: s3, S4: s4, S5: s5, S6: s6 };

export function getStore(id: string) { return storeList.find((store) => store.id === id); }
export function getAdvice(storeId: string, date: string) { return adviceData[date]?.[storeId]; }
export function getSales(storeId: string) { return salesData[storeId]; }
export function adviceIsVisible(advice?: Json) { return Boolean(advice?.validation?.at(-1)?.passed); }
