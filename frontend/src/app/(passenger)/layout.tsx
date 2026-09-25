import { RoleGate } from "@/components/layout/RoleGate";

const ROLES = ["PASSENGER"] as const;

export default function PassengerLayout({ children }: { children: React.ReactNode }) {
  return <RoleGate roles={ROLES}>{children}</RoleGate>;
}
