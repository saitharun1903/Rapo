import { RoleGate } from "@/components/layout/RoleGate";

const ROLES = ["ADMIN"] as const;

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <RoleGate roles={ROLES}>{children}</RoleGate>;
}
