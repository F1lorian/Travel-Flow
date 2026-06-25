export interface UserEventDTO {
  userId: number;
  username: string;
  firstname: string;
  surname: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
}
