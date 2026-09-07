import { Link } from 'react-router-dom'

export default function Home() {
	return (
		<div className="p-8">
			<h1 className="text-2xl font-bold mb-4">Welcome</h1>
			<p className="text-gray-600 mb-4">A simple client management app.</p>
			<Link to="/clients" className="text-blue-600 underline">View Clients →</Link>
		</div>
	)
}