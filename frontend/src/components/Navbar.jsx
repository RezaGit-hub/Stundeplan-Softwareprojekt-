import { Link } from 'react-router-dom'

export default function Navbar() {
	return (
		<nav className="bg-slate-800 text-white px-6 py-4 flex gap-6">
		<Link to="/" className="font-semibold hover:text-slate-300">Home</Link>
		<Link to="/clients" className="hover:text-slate-300">Clients</Link>
		</nav>
	)
}